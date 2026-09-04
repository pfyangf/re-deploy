package com.redeploy.repository;

import com.redeploy.model.DownloadSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DownloadSessionMapper {

    @Insert("INSERT INTO download_session (server_id, remote_path, file_size, bytes_received, " +
            "status, local_path, initiator, initiator_ip, created_at, updated_at) " +
            "VALUES (#{serverId}, #{remotePath}, #{fileSize}, 0, " +
            "#{status}, NULL, #{initiator}, #{initiatorIp}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DownloadSession session);

    @Update("UPDATE download_session SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE download_session SET bytes_received=#{bytes}, updated_at=NOW() WHERE id=#{id}")
    int updateBytes(@Param("id") Long id, @Param("bytes") long bytes);

    @Update("UPDATE download_session SET md5=#{md5}, bytes_received=#{bytes}, status='SUCCESS', " +
            "local_path=#{localPath}, completed_at=NOW(), updated_at=NOW() WHERE id=#{id}")
    int markSuccess(@Param("id") Long id, @Param("md5") String md5,
                    @Param("bytes") long bytes, @Param("localPath") String localPath);

    @Update("UPDATE download_session SET status='FAILED', error_message=#{err}, " +
            "completed_at=NOW(), updated_at=NOW() WHERE id=#{id}")
    int markFailed(@Param("id") Long id, @Param("err") String err);

    @Update("UPDATE download_session SET status='CANCELLED', completed_at=NOW(), updated_at=NOW() " +
            "WHERE id=#{id} AND status IN ('PENDING','DOWNLOADING')")
    int markCancelled(Long id);

    @Select("SELECT * FROM download_session WHERE id=#{id}")
    DownloadSession findById(Long id);

    @Select("SELECT * FROM download_session WHERE server_id=#{serverId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DownloadSession> listByServer(@Param("serverId") Long serverId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("SELECT * FROM download_session " +
            "WHERE (#{serverId} IS NULL OR server_id = #{serverId}) " +
            "  AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DownloadSession> listByFilter(@Param("serverId") Long serverId,
                                       @Param("status") String status,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM download_session " +
            "WHERE (#{serverId} IS NULL OR server_id = #{serverId}) " +
            "  AND (#{status} IS NULL OR status = #{status})")
    long countByFilter(@Param("serverId") Long serverId, @Param("status") String status);

    @Delete("DELETE FROM download_session WHERE id=#{id}")
    int deleteById(Long id);

    /** 启动期把上次没跑完的 downloading 标 failed（崩溃恢复） */
    @Update("UPDATE download_session SET status='FAILED', error_message='server crashed during download', " +
            "completed_at=NOW(), updated_at=NOW() " +
            "WHERE status IN ('PENDING','DOWNLOADING') AND updated_at < #{cutoff}")
    int markStaleDownloadingAsFailed(@Param("cutoff") LocalDateTime cutoff);

    /** 清理 30 天前已成功的 */
    @Delete("DELETE FROM download_session WHERE status='SUCCESS' AND created_at < #{cutoff}")
    int deleteSuccessOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
