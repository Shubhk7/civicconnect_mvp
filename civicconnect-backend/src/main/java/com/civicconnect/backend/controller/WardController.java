package com.civicconnect.backend.controller;

import com.civicconnect.backend.model.Ward;
import com.civicconnect.backend.repository.WardRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wards")
public class WardController {

    private final WardRepository wardRepository;

    public WardController(WardRepository wardRepository) {
        this.wardRepository = wardRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return wardRepository.findAll().stream()
            .map(w -> Map.<String, Object>of("id", w.getId(), "name", w.getName(), "city", w.getCity()))
            .toList();
    }
}
