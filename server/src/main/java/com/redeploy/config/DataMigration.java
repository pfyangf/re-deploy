package com.redeploy.config;

import com.redeploy.model.Group;
import com.redeploy.model.Server;
import com.redeploy.repository.GroupMapper;
import com.redeploy.repository.ServerMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataMigration {

    private final GroupMapper groupMapper;
    private final ServerMapper serverMapper;
    private final JdbcTemplate jdbcTemplate;

    public DataMigration(GroupMapper groupMapper, ServerMapper serverMapper, DataSource dataSource) {
        this.groupMapper = groupMapper;
        this.serverMapper = serverMapper;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Ensure default group exists
        if (groupMapper.findByName("default").isEmpty()) {
            Group defaultGroup = new Group();
            defaultGroup.setName("default");
            defaultGroup.setDescription("默认分组");
            groupMapper.insert(defaultGroup);
            System.out.println("Initialized default group 'default'");
        }

        // For existing databases, ensure new columns exist (schema.sql only handles new tables)
        ensureColumnExists("servers", "group_id", "INTEGER");
        ensureColumnExists("servers", "ssh_username", "VARCHAR(100)");
        ensureColumnExists("servers", "ssh_password", "VARCHAR(255)");
        ensureColumnExists("servers", "ssh_private_key", "TEXT");
        ensureColumnExists("servers", "ssh_port", "INTEGER DEFAULT 22");
        ensureColumnExists("tasks", "group_id", "INTEGER");

        // Migrate existing data: group_name -> group_id
        migrateGroupNamesToGroupId();
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDef) {
        try {
            // Check if column exists by querying PRAGMA table_info
            boolean exists = Boolean.TRUE.equals(jdbcTemplate.query(
                    "PRAGMA table_info(" + tableName + ")",
                    (ResultSet rs) -> {
                        while (rs.next()) {
                            if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                                return Boolean.TRUE;
                            }
                        }
                        return Boolean.FALSE;
                    }
            ));
            if (!exists) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
                System.out.println("Added column " + columnName + " to " + tableName);
            }
        } catch (Exception e) {
            System.err.println("Failed to ensure column " + tableName + "." + columnName + ": " + e.getMessage());
        }
    }

    private void migrateGroupNamesToGroupId() {
        List<Server> servers = serverMapper.findAll();
        Map<String, Long> groupNameToId = new HashMap<>();

        // Load all existing groups
        List<Group> groups = groupMapper.findAll();
        for (Group g : groups) {
            groupNameToId.put(g.getName(), g.getId());
        }

        // Get default group id
        Long defaultGroupId = groupMapper.findByName("default")
                .map(Group::getId)
                .orElse(null);

        int migrated = 0;
        for (Server server : servers) {
            if (server.getGroupId() != null) {
                continue; // Already migrated
            }

            if (server.getGroupName() != null && !server.getGroupName().trim().isEmpty()) {
                String groupName = server.getGroupName().trim();
                Long groupId = groupNameToId.get(groupName);
                if (groupId == null) {
                    // Create new group from existing name
                    Group newGroup = new Group();
                    newGroup.setName(groupName);
                    newGroup.setDescription("Migrated from existing group name");
                    groupMapper.insert(newGroup);
                    groupId = newGroup.getId();
                    groupNameToId.put(groupName, groupId);
                }
                server.setGroupId(groupId);
            } else {
                // No group name, assign to default
                server.setGroupId(defaultGroupId);
            }
            serverMapper.update(server);
            migrated++;
        }

        if (migrated > 0) {
            System.out.println("Data migration completed: migrated " + migrated + " servers to group_id");
        }
    }
}
