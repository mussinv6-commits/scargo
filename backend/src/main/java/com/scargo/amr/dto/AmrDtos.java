package com.scargo.amr.dto;

import java.time.OffsetDateTime;
import java.util.List;

// 26.10.14 AMR 연동 요청 · 응답 (웹 관제 화면 · AMR 앱이 같이 쓴다)
public class AmrDtos {

    // ── 요청 ──
    public record TelemetryItem(String code, String state, Double vibration, Double temp, Double battery,
                                Double health, Double cumLoadKg, Double speed, Double x, Double y) {}
    public record TelemetryRequest(Integer phase, List<TelemetryItem> items) {}
    public record PredictionRequest(String code, Double rulHours, Double vibration, Double slope, Double cumLoadKg, String reason) {}
    public record ApproveRequest(String by) {}
    public record EventRequest(String code, String level, Integer phase, String message) {}
    public record SettingsRequest(Integer rulThresholdHours, Integer chargeAtPct, String by) {}
    // 26.10.15 허브 입출차 OCR: direction ENTRY/EXIT, kind = 들어오는 차 쪽 카메라 폴더(CARGO/COURIER, 판정은 DB가 함)
    // 26.10.15 KPI: AMR 운반 1건 기록 (웹 관제가 운반 끝날 때마다 보냄)
    public record TaskRequest(String code, String taskType, String waybillNo, String barcode, String hub, String handleType,
                              Double weightKg, String fromLoc, String toLoc, Double interWaitSec,
                              OffsetDateTime startedAt, OffsetDateTime finishedAt) {}
    public record VehicleScanRequest(String direction, String kind, Integer dock, String vehicleNo,
                                     Integer boxCount, List<String> barcodes, String destHub) {}

    // ── 응답 ──
    public record RobotView(String code, String type, String role, String state, Double battery, Double health,
                            Double cumLoadKg, Double vibration, Double temp, OffsetDateTime updatedAt) {}
    public record MaintView(Long id, String robotCode, String spareCode, String status, String partName, String memo,
                            Double rulHours, Double vibration, OffsetDateTime scheduledAt, OffsetDateTime approvedAt,
                            String approvedBy, OffsetDateTime startedAt, OffsetDateTime completedAt) {}
    public record EventView(Long id, String robotCode, String level, Integer phase, String message, OffsetDateTime createdAt) {}
    public record SettingsView(Integer rulThresholdHours, Integer chargeAtPct, String updatedBy, OffsetDateTime updatedAt) {}
    public record VehicleView(Long id, String direction, String plate, String photoPlate, String kind, String decision,
                              String lane, Integer dock, Double confidence, String engine, String imageUrl, Integer boxCount,
                              String destHub, OffsetDateTime createdAt) {}
    public record HourCount(String hour, long count) {}
    public record RobotUtil(String code, int pct) {}
    public record KpiView(long today, long yesterdaySameTime, List<HourCount> hourly, Double avgTransportSec,
                          Double avgInterWaitSec, long interWaits, List<RobotUtil> utilization,
                          long entries, long exits, long blocks, Double avgOcrConfidence, OffsetDateTime at) {}
    public record RegistryView(String plate, String kind, String carrier, String memo) {}
    public record StateView(boolean producerLive, OffsetDateTime producerAt, Integer phase, List<RobotView> robots,
                            MaintView pending, MaintView active, List<EventView> events, SettingsView settings,
                            List<VehicleView> vehicles) {}
    public record SensorPoint(OffsetDateTime t, Double vibration, Double health, String state) {}
}
