package com.example.notification.api.controller;

import com.example.notification.api.dto.CreateTemplateRequest;
import com.example.notification.api.dto.TemplateResponse;
import com.example.notification.application.usecase.TemplateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateUseCase useCase;

    public TemplateController(TemplateUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request) {
        var template = useCase.create(
                request.id(), request.name(), request.channel(),
                request.subject(), request.body()
        );
        return TemplateResponse.from(template);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable String id) {
        return TemplateResponse.from(useCase.byId(id));
    }
}
