package com.scargo.ops;

import com.scargo.app.entity.Hub;
import com.scargo.app.repository.HubRepository;
import com.scargo.entity.Account;
import com.scargo.ops.entity.*;
import com.scargo.ops.repository.*;
import com.scargo.ops.service.OpsAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 26.10.20 h2 프로필 전용 시연 데이터. logistics_centers: 1 곤지암 허브(HUB), 2 인천·3 서울·4 경기 센터(DISTRIBUTION_CENTER) */
@Component
@Profile("h2")
@RequiredArgsConstructor
public class OpsDemoSeeder implements CommandLineRunner {
    private final OpsAccountService accountService;
    private final PasswordEncoder enc;
    private final LogisticsCenterRepository centers;
    private final HubRepository hubs;
    private final HubDockRepository docks;
    private final ShipmentLegRepository legs;
    private final BranchUnitRepository units;
    private final ItemRepository items;
    private final ParcelRepository parcels;
    private final ContainerSealRepository seals;
    private final TollPassageRepository tolls;
    private final com.scargo.repository.CompanyRepository companies;
    private final com.scargo.repository.TruckRepository trucks;
    private final com.scargo.app.repository.TransportOrderRepository orders;

    @Override
    public void run(String... args) {
        // 26.10.20 최종: logistics_centers(HUB) 먼저 → hubs (위치·주소는 센터에서 조인)
        Long hubC = center("GJA-HUB", "곤지암 허브", "HUB", null);
        LogisticsCenter hc = centers.findById(hubC).orElseThrow();
        hc.setAddress("경기도 광주시 곤지암읍"); hc.setLatitude(new BigDecimal("37.35")); hc.setLongitude(new BigDecimal("127.30")); centers.save(hc);
        Long ic = center("DC-INCHEON", "인천 센터", "DISTRIBUTION_CENTER", 1L);
        Long se = center("DC-SEOUL", "서울 센터", "DISTRIBUTION_CENTER", 2L);
        Long gg = center("DC-GYEONGGI", "경기 센터", "DISTRIBUTION_CENTER", 3L);
        Hub h = new Hub();
        h.setCenterId(hubC); h.setHubCode("HUB-GJA"); h.setHubName("곤지암 허브");
        h = hubs.save(h);

        // 운송사 2곳 (회사 단위 승인) + 담당자 + 차량
        com.scargo.entity.Company ca = carrier("한빛운송", "서울시 강서구 1", "111-11-11111", true);
        com.scargo.entity.Company cb = carrier("두리물류", "인천시 중구 2", "222-22-22222", true);
        com.scargo.entity.Company cp = carrier("대기운송", "경기도 3", "333-33-33333", false);
        contact("carrier1", "한빛담당", ca); contact("carrier2", "두리담당", cb); contact("carrier3", "대기담당", cp);
        truck("서울84차0002", ca); truck("서울84차0003", ca); truck("인천80바9090", ca); truck("인천85바7777", cb);

        acc("control1", "관제사A", Account.UserType.CONTROL, null);
        acc("admin1", "관리자", Account.UserType.ADMIN, null);
        accHub("hubop1", "김허브", h.getHubId());
        acc("centerop1", "이센터", Account.UserType.CENTER_OP, se);
        Account drv = acc("lh01", "최지선", Account.UserType.BRANCH_DRIVER, null, ca.getCompanyId());
        acc("lh02", "박지선", Account.UserType.BRANCH_DRIVER, null, cb.getCompanyId());
        Account ct = acc("ct01", "정컨테이너", Account.UserType.CONTAINER_DRIVER, null, ca.getCompanyId());
        // 검증 확인: HUB_OP 에 센터를 넣으면 거부되어야 한다
        try { acc("bad1", "잘못", Account.UserType.HUB_OP, se);  /* center_id 로 HUB_OP → 거부 */ throw new IllegalStateException("검증 실패"); } catch (IllegalArgumentException ok) { /* 정상 거부 */ }

        for (String[] d : new String[][]{{"D1", "IN"}, {"D2", "IN"}, {"D11", "OUT"}, {"D12", "OUT"}}) {
            HubDock x = new HubDock(); x.setHubId(h.getHubId()); x.setDockCode(d[0]); x.setDockType(d[1]); x.setStatus("EMPTY"); docks.save(x);
        }
        ShipmentLeg l = new ShipmentLeg();
        l.setLegType("HUB_TO_CENTER"); l.setFromCenterId(hubC); l.setToCenterId(se);
        l.setVehicleNo("서울84차0002"); l.setDriverAccountId(drv.getAccountId()); l.setPlannedUnits(2);
        legs.save(l);
        unit("RT-SE-01", se, h.getHubId(), se, se, se);
        unit("RT-SE-02", se, h.getHubId(), se, se);
        unit("RT-GG-07", gg, h.getHubId(), gg);           // 다른 센터행 롤테이너
        unit("RT-SE-03", se, h.getHubId(), se, gg);       // 서울 롤테이너에 경기행 박스 (SORT_FAILED)

        ContainerSeal s = new ContainerSeal(); s.setShipmentId(1L); s.setContainerNo("MSCU4788258"); s.setSealNo("KR1234567"); s.setAttachedByName("선사"); seals.save(s);
        // 컨테이너 기사 T-02 시연: 오더 + 선사 씰
        ContainerSeal s2 = new ContainerSeal(); s2.setShipmentId(2L); s2.setContainerNo("SCGU1234565"); s2.setSealNo("ML-772018"); s2.setAttachedByName("선사"); seals.save(s2);
        com.scargo.app.entity.TransportOrder o = new com.scargo.app.entity.TransportOrder();
        o.setOrderNo("TO-261020-004"); o.setCompanyId(ca.getCompanyId()); o.setContainerNo("SCGU1234565"); o.setVehicleNo("서울84차0003");
        o.setDriverAccountId(ct.getAccountId()); o.setOriginTerminalId(1L); o.setDestHubId(h.getHubId()); o.setStatus("ASSIGNED");
        o.setAssignedAt(java.time.OffsetDateTime.now()); orders.save(o);
        TollPassage p = new TollPassage(); p.setTollgateId(1L); p.setVehicleNo("인천80바9090"); p.setRecognitionStatus("SUCCESS");
        p.setPassageStatus("BLOCKED"); p.setBlockReason("OVERLOAD"); p.setCheckId(1L); p.setAssignmentId(l.getAssignmentId()); tolls.save(p);
    }

    private Long center(String code, String name, String type, Long region) {
        LogisticsCenter c = new LogisticsCenter(); c.setCenterCode(code); c.setCenterName(name); c.setCenterType(type); c.setRegionId(region);
        return centers.save(c).getCenterId();
    }

    private Account accHub(String id, String name, Long hubId) {
        Account a = Account.builder().userId(id).userName(name).userPw(enc.encode("demo1234")).userType(Account.UserType.HUB_OP).build();
        a.setHubId(hubId);
        return accountService.save(a);
    }

    private Account acc(String id, String name, Account.UserType t, Long center) { return acc(id, name, t, center, null); }

    private Account acc(String id, String name, Account.UserType t, Long center, Long company) {
        Account a = Account.builder().userId(id).userName(name).userPw(enc.encode("demo1234")).userType(t).companyId(company).build();
        a.setCenterId(center);
        return accountService.save(a);
    }

    private com.scargo.entity.Company carrier(String name, String addr, String bno, boolean approve) {
        com.scargo.entity.Company c = com.scargo.entity.Company.builder().companyName(name).address(addr).businessNo(bno).industryType("화물운송").representativeName("대표").build();
        c.markCarrierPending();
        if (approve) c.approveCarrier(null);
        return companies.save(c);
    }

    private void contact(String id, String name, com.scargo.entity.Company c) {
        accountService.save(Account.builder().userId(id).userName(name).userPw(enc.encode("demo1234")).userType(Account.UserType.CORPORATE_APPROVED)
                .companyId(c.getCompanyId()).businessNo(c.getBusinessNo()).build());
    }

    private void truck(String no, com.scargo.entity.Company c) {
        com.scargo.entity.Truck t = new com.scargo.entity.Truck(); t.setVehicleNo(no); t.setCompany(c); t.setTruckType("카고"); trucks.save(t);
    }

    private int seq = 0;
    private void unit(String id, Long dest, Long hubId, Long... boxDests) {
        BranchUnit u = new BranchUnit(); u.setUnitId(id); u.setDestCenterId(dest); u.setStatus("SORTING"); units.save(u);
        for (Long bd : boxDests) {
            seq++;
            Item i = new Item(); i.setTrackingNo("TRK" + seq); i.setBarcode("BC" + seq); i.setShipmentId(1L); i.setItemName("박스 " + seq);
            i.setDestinationCenterId(bd); i.setStatus("SORTING"); i.setWeightKg(new BigDecimal("12.5")); items.save(i);
            Parcel p = new Parcel(); p.setParcelNo("BX-" + String.format("%04d", seq)); p.setItemId(i.getItemId()); p.setHubId(hubId); p.setStatus("OUTBOUND_READY"); p.setUnitId(id);
            parcels.save(p);
        }
    }
}
