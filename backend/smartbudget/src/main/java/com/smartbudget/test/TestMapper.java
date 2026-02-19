package com.smartbudget.test;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestMapper {

  List<TestDTO> selectTest() throws Exception;

}