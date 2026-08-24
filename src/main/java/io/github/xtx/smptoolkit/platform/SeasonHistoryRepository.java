package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

final class SeasonHistoryRepository {
    private final SMPToolkitPlugin plugin;
    SeasonHistoryRepository(SMPToolkitPlugin plugin){this.plugin=plugin;}

    CompletableFuture<List<SeasonEntry>> list(){
        return CompletableFuture.supplyAsync(()->{
            List<SeasonEntry> out=new ArrayList<>();
            File db=new File(plugin.getDataFolder(),plugin.getConfig().getString("database.file","data.db"));
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.getAbsolutePath());PreparedStatement ps=c.prepareStatement("SELECT id,display_name,status,started_at,ended_at FROM smp_seasons ORDER BY started_at DESC LIMIT 100");ResultSet rs=ps.executeQuery()){
                while(rs.next()){long ended=rs.getLong(5);out.add(new SeasonEntry(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.wasNull()?null:ended));}
            }catch(SQLException ex){throw new CompletionException(ex);}
            return out;
        });
    }

    record SeasonEntry(String id,String displayName,String status,long startedAt,Long endedAt){}
}
