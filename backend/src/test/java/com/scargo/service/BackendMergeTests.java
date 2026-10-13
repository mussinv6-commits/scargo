package com.scargo.service;

import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.OverloadCheckCreateRequest;
import com.scargo.dto.WeighingRequest;
import com.scargo.entity.Company;
import com.scargo.entity.Truck;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.OverloadCheck;
import com.scargo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackendMergeTests {
    private GateLogRepository gateLogs;
    private GateRepository gates;
    private NotificationService notifications;
    private LoadingRecordRepository loadingRecords;
    private TruckWorkflowService workflow;
    private GateLogService gateService;

    @BeforeEach
    void setUp() {
        gateLogs = mock(GateLogRepository.class);
        gates = mock(GateRepository.class);
        notifications = mock(NotificationService.class);
        loadingRecords = mock(LoadingRecordRepository.class);
        workflow = mock(TruckWorkflowService.class);
        gateService = new GateLogService(gateLogs, gates, notifications, loadingRecords, workflow);
        when(gates.findById(1L)).thenReturn(Optional.of(Gate.builder().gateId(1L).gateName("Gate").build()));
        when(gateLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void entryStillStartsPendingLoadingRecord() {
        LoadingRecord record = mock(LoadingRecord.class);
        when(record.getRecordId()).thenReturn(42L);
        when(loadingRecords.findFirstByTruck_VehicleNoAndStatusOrderByRecordIdDesc(
                "truck", LoadingRecord.LoadingStatus.PENDING)).thenReturn(Optional.of(record));
        gateService.createGateLog(GateLogCreateRequest.builder().gateId(1L)
                .actualVehicleNo("truck").scanType("ENTRY").build(), null, null);
        verify(workflow).processFirstOcr("truck", 42L);
        verifyNoInteractions(notifications);
    }

    @Test
    void exitStillRunsFinalOcr() {
        gateService.createGateLog(GateLogCreateRequest.builder().gateId(1L)
                .actualVehicleNo("truck").scanType("EXIT").build(), null, null);
        verify(workflow).processFinalOcr("truck");
    }

    @Test
    void failedRecognitionDoesNotStartWorkflow() {
        gateService.createGateLog(GateLogCreateRequest.builder().gateId(1L)
                .actualVehicleNo("truck").scanType("ENTRY").recognitionStatus("FAILED").build(), null, null);
        verifyNoInteractions(workflow, loadingRecords, notifications);
    }

    @Test
    void failedNotificationDoesNotPreventGateRecordOrOtherRecipients() {
        when(notifications.findAdminAccountIds()).thenReturn(List.of(10L, 11L));
        when(notifications.createNotificationInNewTx(any()))
                .thenThrow(new IllegalStateException("notification unavailable")).thenReturn(null);
        assertNotNull(gateService.createGateLog(GateLogCreateRequest.builder().gateId(1L)
                .recognizedPlateNo("unknown").build(), null, null));
        verify(gateLogs).save(any(GateLog.class));
        verify(notifications, times(2)).createNotificationInNewTx(any());
        verifyNoInteractions(workflow);
    }

    @Test
    void overloadNotifiesAdminsAndContinuesAfterRecipientFailure() {
        OverloadCheckRepository checks = mock(OverloadCheckRepository.class);
        TruckRepository trucks = mock(TruckRepository.class);
        OverloadService service = new OverloadService(checks, notifications, trucks);
        when(checks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(trucks.findByVehicleNo("truck")).thenReturn(Optional.empty());
        when(notifications.findAdminAccountIds()).thenReturn(List.of(10L, 11L));
        when(notifications.createNotificationInNewTx(any()))
                .thenThrow(new IllegalStateException("notification unavailable")).thenReturn(null);
        assertNotNull(service.createCheck(OverloadCheckCreateRequest.builder()
                .vehicleNo("truck").isViolation(true).isPassed(false).build()));
        ArgumentCaptor<NotificationCreateRequest> requests = ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notifications, times(2)).createNotificationInNewTx(requests.capture());
        assertEquals(List.of(10L, 11L), requests.getAllValues().stream()
                .map(NotificationCreateRequest::getAccountId).toList());
        verify(checks).save(any(OverloadCheck.class));
    }

    @Test
    void overloadNotifiesEveryCompanyAccountAndAdmin() {
        TruckRepository trucks = mock(TruckRepository.class);
        Truck truck = mock(Truck.class);
        Company company = mock(Company.class);
        when(truck.getCompany()).thenReturn(company);
        when(company.getCompanyId()).thenReturn(7L);
        when(trucks.findByVehicleNo("truck")).thenReturn(Optional.of(truck));
        when(notifications.findCompanyAccountIds(7L)).thenReturn(List.of(20L, 21L));
        when(notifications.findAdminAccountIds()).thenReturn(List.of(10L));
        OverloadService service = new OverloadService(mock(OverloadCheckRepository.class), notifications, trucks);
        service.notifyOverload(OverloadCheck.builder().vehicleNo("truck").checkId(4L).build(), false);
        ArgumentCaptor<NotificationCreateRequest> requests = ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notifications, times(3)).createNotificationInNewTx(requests.capture());
        assertEquals(List.of(20L, 21L, 10L), requests.getAllValues().stream()
                .map(NotificationCreateRequest::getAccountId).toList());
        assertEquals(7L, requests.getAllValues().get(0).getCompanyId());
        assertNull(requests.getAllValues().get(2).getCompanyId());
    }

    @Test
    void reweighOnlyNotifiesWhenStillOverloaded() {
        OverloadCheckRepository checks = mock(OverloadCheckRepository.class);
        OverloadService overload = mock(OverloadService.class);
        WeighbridgeService service = new WeighbridgeService(gateLogs, checks, mock(TruckRepository.class),
                overload, mock(ContainerRepository.class), loadingRecords, new ObjectMapper());
        ReflectionTestUtils.setField(service, "axleLimitKg", 10000);
        ReflectionTestUtils.setField(service, "grossLimitKg", 40000);
        OverloadCheck check = OverloadCheck.builder().checkId(4L).vehicleNo("truck")
                .isPassed(false).retryCount(0).build();
        when(checks.findById(4L)).thenReturn(Optional.of(check));
        WeighingRequest request = new WeighingRequest();
        request.setAxles(List.of(new WeighingRequest.Axle(11000, null, null),
                new WeighingRequest.Axle(8000, null, null)));
        service.reweigh(4L, request);
        verify(overload).notifyOverload(check, true);
        assertEquals(1, check.getRetryCount());
        request.setAxles(List.of(new WeighingRequest.Axle(8000, null, null),
                new WeighingRequest.Axle(8000, null, null)));
        service.reweigh(4L, request);
        verify(overload, times(1)).notifyOverload(check, true);
        assertTrue(check.getIsPassed());
        assertEquals(2, check.getRetryCount());
    }
}
