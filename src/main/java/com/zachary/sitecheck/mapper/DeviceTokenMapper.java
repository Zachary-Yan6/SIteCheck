package com.zachary.sitecheck.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Push tokens of users' phones (FR-27). */
@Mapper
public interface DeviceTokenMapper {

    /**
     * Saves the token for this user. If the phone was used by someone else before, the token
     * moves to the new user - reminders follow whoever is logged in on the phone now.
     */
    @Insert("""
            INSERT INTO device_tokens (token, user_id, platform, updated_at)
            VALUES (#{token}, #{userId}, #{platform}, now())
            ON CONFLICT (token) DO UPDATE
                SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, updated_at = now()
            """)
    void upsert(@Param("token") String token, @Param("userId") Long userId, @Param("platform") String platform);

    /** On logout: only the owner can remove their token. */
    @Delete("DELETE FROM device_tokens WHERE token = #{token} AND user_id = #{userId}")
    int deleteOwn(@Param("token") String token, @Param("userId") Long userId);

    /** Expo said the app was uninstalled or the token is no longer valid. */
    @Delete("DELETE FROM device_tokens WHERE token = #{token}")
    void delete(@Param("token") String token);

    @Select("SELECT user_id, token FROM device_tokens")
    List<UserToken> selectAll();

    record UserToken(Long userId, String token) {
    }
}
