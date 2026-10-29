package com.dam.common.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一分页结果包装类
 * 将 MyBatis-Plus 的 IPage 转换为前端友好的分页结构
 *
 * @param <T> 记录数据类型
 */
@Getter
@Setter
public class PageResult<T> {
    /** 当前页码（从1开始） */
    private long page;
    /** 每页条数 */
    private long pageSize;
    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long totalPages;
    /** 当前页数据列表 */
    private List<T> records;

    /**
     * 从 MyBatis-Plus 分页对象转换
     *
     * @param ipage  MyBatis-Plus 分页查询结果
     * @param <T>    记录数据类型
     * @return 统一分页结果
     */
    public static <T> PageResult<T> of(IPage<T> ipage) {
        PageResult<T> result = new PageResult<>();
        // 当前页码
        result.setPage(ipage.getCurrent());
        // 每页大小
        result.setPageSize(ipage.getSize());
        // 总记录数
        result.setTotal(ipage.getTotal());
        // 总页数
        result.setTotalPages(ipage.getPages());
        // 当前页记录
        result.setRecords(ipage.getRecords());
        return result;
    }
}