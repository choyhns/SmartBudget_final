package com.smartbudget.monthlyreport;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthlyReportMapper {
    List<MonthlyReportDTO> selectReportsByUser(@Param("userId") Long userId) throws Exception;
    MonthlyReportDTO selectReportByYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth) throws Exception;
    MonthlyReportDTO selectCurrentMonthReport(@Param("userId") Long userId) throws Exception;
    int insertMonthlyReport(MonthlyReportDTO report) throws Exception;
    int updateMonthlyReport(MonthlyReportDTO report) throws Exception;
    int clearCurrentMonthFlag(@Param("userId") Long userId) throws Exception;
    int deleteMonthlyReport(@Param("reportId") Long reportId) throws Exception;
}
