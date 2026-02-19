package com.smartbudget.test;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequiredArgsConstructor
public class TestController {

  private final TestService testService;

  @GetMapping("/api/test/selectTest")
  public List<TestDTO> selectTest() throws Exception {
      return testService.selectTest();
  }
  

}