package com.smartbudget.test;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestService {
  
  @Autowired
  TestMapper testMapper;

  public List<TestDTO> selectTest() throws Exception {
    return testMapper.selectTest();
  }

}
