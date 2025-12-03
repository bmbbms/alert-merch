package com.alert.merch.mapper;

import com.alert.merch.model.TaskInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务数据访问接口
 */
@Mapper
public interface TaskMapper {
    
    /**
     * 查询超时任务
     * 
     * @param days 查询天数范围
     * @return 任务列表
     */
    List<TaskInfo> selectTimeoutTasks(@Param("days") int days);
    
    /**
     * 根据任务状态查询任务
     * 
     * @param days 查询天数范围
     * @param procKey 流程键
     * @param taskKeys 任务键列表
     * @return 任务列表
     */
    List<TaskInfo> selectTasksByStatus(
        @Param("days") int days,
        @Param("procKey") String procKey,
        @Param("taskKeys") List<String> taskKeys
    );
    
    /**
     * 查询当天任务
     * 
     * @param procKey 流程键
     * @param taskKeys 任务键列表
     * @return 任务列表
     */
    List<TaskInfo> selectTodayTasks(
        @Param("procKey") String procKey,
        @Param("taskKeys") List<String> taskKeys
    );
}
