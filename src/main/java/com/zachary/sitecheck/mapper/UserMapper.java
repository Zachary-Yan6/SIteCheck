package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** CRUD for {@link User}. */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** Inspectors assigned to one site, by name. */
    @Select("""
            SELECT u.*
            FROM users u
            JOIN site_inspectors si ON si.user_id = u.id
            WHERE si.site_id = #{siteId}
            ORDER BY u.full_name
            """)
    List<User> selectAssignedToSite(Long siteId);
}
