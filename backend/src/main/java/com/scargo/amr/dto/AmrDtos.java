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

    // ── 응답 ──
    public record RobotView(String code, String type, String role, String state, Double battery, Double health,
                            Double cumLoadKg, Double vibration, Double temp, OffsetDateTime updatedAt) {}
    public record MaintView(Long id, String robotCode, String spareCode, String status, String partName, String memo,
                            Double rulHours, Double vibration, OffsetDateTime scheduledAt, OffsetDateTime approvedAt,
                            String approvedBy, OffsetDateTime startedAt, OffsetDateTime completedAt) {}
    public record EventView(Long id, String robotCode, String level, Integer phase, String message, OffsetDateTime createdAt) {}
    public record StateView(boolean producerLive, OffsetDateTime producerAt, Integer phase, List<RobotView> robots,
                            MaintView pending, MaintView active, List<EventView> events) {}
    public record SensorPoint(OffsetDateTime t, Double vibration, Double health, String state) {}
}
