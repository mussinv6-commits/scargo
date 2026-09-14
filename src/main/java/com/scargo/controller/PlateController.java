package com.scargo.controller;

import com.scargo.client.PlateInspectionClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

// 프론트에서 사진 받아서 FastAPI로 넘겨주는 중계 역할
@RestController
@RequestMapping("/api/plates")
public class PlateController {

    private final PlateInspectionClient plateInspectionClient;

    public PlateController(PlateInspectionClient plateInspectionClient) {
        this.plateInspectionClient = plateInspectionClient;
    }

    @PostMapping("/inspect")
    public Map<String, Object> inspect(@RequestParam("file") MultipartFile file) throws IOException {
        return plateInspectionClient.inspect(file);
    }
}
