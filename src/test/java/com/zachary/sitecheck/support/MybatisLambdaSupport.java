package com.zachary.sitecheck.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * Lets plain unit tests build MyBatis-Plus lambda wrappers such as {@code .select(Photo::getId)}.
 * Those look up the entity's column names, which Spring normally registers at start-up; without a
 * Spring context we register them here instead.
 */
public final class MybatisLambdaSupport {

    private MybatisLambdaSupport() {
    }

    public static void registerEntities(Class<?>... entities) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "unit-test");
        for (Class<?> entity : entities) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
