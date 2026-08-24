package io.github.xtx.smptoolkit.platform;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlatformDatabase {
    private final SMPToolkitPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SMPToolkit-PlatformDB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public PlatformDatabase(SMPToolkitPlugin plugin) { this.plugin = plugin; }

    public synchronized void init() throws SQLException {
        File db = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "data.db"));
        db.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_cases(id INTEGER PRIMARY KEY AUTOINCREMENT,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,title TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',staff_uuid TEXT,staff_name TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,closed_at INTEGER)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_case_comments(id INTEGER PRIMARY KEY AUTOINCREMENT,case_id INTEGER NOT NULL,staff_uuid TEXT,staff_name TEXT NOT NULL,kind TEXT NOT NULL DEFAULT 'COMMENT',body TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_case_links(id INTEGER PRIMARY KEY AUTOINCREMENT,case_id INTEGER NOT NULL,record_type TEXT NOT NULL,record_id INTEGER NOT NULL,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_bounties(id INTEGER PRIMARY KEY AUTOINCREMENT,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,creator_uuid TEXT NOT NULL,creator_name TEXT NOT NULL,material TEXT NOT NULL,amount INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'ACTIVE',created_at INTEGER NOT NULL,claimed_at INTEGER,claimed_by_uuid TEXT,claimed_by_name TEXT)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_smp_bounties_target ON smp_bounties(target_uuid,status)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_counters(uuid TEXT NOT NULL,counter_key TEXT NOT NULL,value INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(uuid,counter_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_quest_progress(uuid TEXT NOT NULL,quest_id TEXT NOT NULL,period_key TEXT NOT NULL,progress INTEGER NOT NULL DEFAULT 0,claimed INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(uuid,quest_id,period_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_achievements(uuid TEXT NOT NULL,achievement_id TEXT NOT NULL,unlocked_at INTEGER NOT NULL,PRIMARY KEY(uuid,achievement_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_staff_activity(id INTEGER PRIMARY KEY AUTOINCREMENT,staff_uuid TEXT,staff_name TEXT NOT NULL,action_type TEXT NOT NULL,detail TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_smp_staff_activity_staff ON smp_staff_activity(staff_uuid,created_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_undo(id INTEGER PRIMARY KEY AUTOINCREMENT,actor_uuid TEXT,actor_name TEXT NOT NULL,action_type TEXT NOT NULL,payload TEXT NOT NULL,created_at INTEGER NOT NULL,undone INTEGER NOT NULL DEFAULT 0,undone_at INTEGER)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_seasons(id TEXT PRIMARY KEY,display_name TEXT NOT NULL,status TEXT NOT NULL,started_at INTEGER NOT NULL,ended_at INTEGER)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_season_stats(season_id TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT NOT NULL,kills INTEGER NOT NULL DEFAULT 0,deaths INTEGER NOT NULL DEFAULT 0,chat_wins INTEGER NOT NULL DEFAULT 0,playtime_ms INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(season_id,uuid))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_combat_log(id INTEGER PRIMARY KEY AUTOINCREMENT,killer_uuid TEXT,killer_name TEXT,victim_uuid TEXT NOT NULL,victim_name TEXT NOT NULL,weapon TEXT,distance REAL NOT NULL DEFAULT 0,assists TEXT,revenge INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_onboarding(uuid TEXT PRIMARY KEY,rules_accepted INTEGER NOT NULL DEFAULT 0,completed_at INTEGER)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS smp_schedule_runs(job_id TEXT PRIMARY KEY,last_run INTEGER NOT NULL)");
        }
    }

    public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
        synchronized (this) {
            if (connection != null) try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    private CompletableFuture<Void> run(SqlRunnable action) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try { action.run(connection); }
                catch (SQLException ex) { throw new CompletionException(ex); }
            }
        }, executor);
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try { return action.get(connection); }
                catch (SQLException ex) { throw new CompletionException(ex); }
            }
        }, executor);
    }

    public CompletableFuture<Boolean> removeWarning(int id) {
        return supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active=0 WHERE id=? AND type='WARN' AND active=1")) {
                ps.setInt(1,id); return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> reactivatePunishment(int id) {
        return supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active=1 WHERE id=?")) { ps.setInt(1,id); return ps.executeUpdate()>0; }
        });
    }

    public CompletableFuture<Boolean> pardonPunishment(int id) {
        return supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active=0 WHERE id=? AND active=1")) { ps.setInt(1,id); return ps.executeUpdate()>0; }
        });
    }

    public CompletableFuture<List<PunishmentRow>> warnings(UUID uuid) {
        return supply(c -> {
            List<PunishmentRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id,reason,staff_name,created_at,active FROM punishments WHERE uuid=? AND type='WARN' ORDER BY id DESC LIMIT 200")) {
                ps.setString(1,uuid.toString());
                try (ResultSet rs=ps.executeQuery()) { while(rs.next()) out.add(new PunishmentRow(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getInt(5)!=0)); }
            }
            return out;
        });
    }

    public CompletableFuture<Integer> openReportCount(UUID target) {
        return supply(c -> {
            try (PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM reports WHERE target_uuid=? AND status<>'CLOSED'")) {
                ps.setString(1,target.toString()); try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):0;}
            }
        });
    }

    public CompletableFuture<List<ReportRow>> reportsForTarget(UUID target) {
        return supply(c -> {
            List<ReportRow> out=new ArrayList<>();
            try(PreparedStatement ps=c.prepareStatement("SELECT id,reporter_name,target_name,reason,status,staff_name,created_at,result FROM reports WHERE target_uuid=? ORDER BY id DESC LIMIT 200")){
                ps.setString(1,target.toString()); try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new ReportRow(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getString(8)));}
            }
            return out;
        });
    }

    public CompletableFuture<Boolean> reopenReport(int id) {
        return supply(c -> { try(PreparedStatement ps=c.prepareStatement("UPDATE reports SET status='OPEN',staff_uuid=NULL,staff_name=NULL,closed_at=NULL,result=NULL WHERE id=?")){ps.setInt(1,id);return ps.executeUpdate()>0;} });
    }

    public CompletableFuture<Integer> createCase(UUID target,String targetName,String title,UUID staff,String staffName) {
        return supply(c -> {
            long now=System.currentTimeMillis();
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_cases(target_uuid,target_name,title,status,staff_uuid,staff_name,created_at,updated_at) VALUES(?,?,?,'OPEN',?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1,target.toString()); ps.setString(2,targetName); ps.setString(3,title);
                if(staff==null)ps.setNull(4,Types.VARCHAR);else ps.setString(4,staff.toString()); ps.setString(5,staffName); ps.setLong(6,now); ps.setLong(7,now); ps.executeUpdate();
                try(ResultSet rs=ps.getGeneratedKeys()){return rs.next()?rs.getInt(1):-1;}
            }
        });
    }

    public CompletableFuture<List<CaseRow>> cases(String status,UUID target) {
        return supply(c -> {
            StringBuilder sql=new StringBuilder("SELECT id,target_uuid,target_name,title,status,staff_name,created_at,updated_at,closed_at FROM smp_cases WHERE 1=1");
            List<Object> params=new ArrayList<>();
            if(status!=null){sql.append(" AND status=?");params.add(status);}
            if(target!=null){sql.append(" AND target_uuid=?");params.add(target.toString());}
            sql.append(" ORDER BY updated_at DESC LIMIT 200");
            List<CaseRow> out=new ArrayList<>();
            try(PreparedStatement ps=c.prepareStatement(sql.toString())){
                for(int i=0;i<params.size();i++)ps.setObject(i+1,params.get(i));
                try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(readCase(rs));}
            }
            return out;
        });
    }

    public CompletableFuture<Optional<CaseRow>> caseById(int id) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT id,target_uuid,target_name,title,status,staff_name,created_at,updated_at,closed_at FROM smp_cases WHERE id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(readCase(rs)):Optional.empty();}}});
    }

    public CompletableFuture<Boolean> claimCase(int id,UUID staff,String staffName) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_cases SET staff_uuid=?,staff_name=?,status='CLAIMED',updated_at=? WHERE id=? AND status<>'CLOSED'")){ps.setString(1,staff.toString());ps.setString(2,staffName);ps.setLong(3,System.currentTimeMillis());ps.setInt(4,id);return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Boolean> closeCase(int id) {
        return supply(c -> {long now=System.currentTimeMillis();try(PreparedStatement ps=c.prepareStatement("UPDATE smp_cases SET status='CLOSED',updated_at=?,closed_at=? WHERE id=? AND status<>'CLOSED'")){ps.setLong(1,now);ps.setLong(2,now);ps.setInt(3,id);return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Boolean> reopenCase(int id) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_cases SET status='OPEN',closed_at=NULL,updated_at=? WHERE id=?")){ps.setLong(1,System.currentTimeMillis());ps.setInt(2,id);return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Void> addCaseComment(int caseId,UUID staff,String staffName,String kind,String body) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_case_comments(case_id,staff_uuid,staff_name,kind,body,created_at) VALUES(?,?,?,?,?,?)")){ps.setInt(1,caseId);if(staff==null)ps.setNull(2,Types.VARCHAR);else ps.setString(2,staff.toString());ps.setString(3,staffName);ps.setString(4,kind);ps.setString(5,body);ps.setLong(6,System.currentTimeMillis());ps.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("UPDATE smp_cases SET updated_at=? WHERE id=?")){ps.setLong(1,System.currentTimeMillis());ps.setInt(2,caseId);ps.executeUpdate();}});
    }

    public CompletableFuture<List<CaseComment>> caseComments(int caseId) {
        return supply(c -> {List<CaseComment> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT id,staff_name,kind,body,created_at FROM smp_case_comments WHERE case_id=? ORDER BY id ASC")){ps.setInt(1,caseId);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new CaseComment(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5)));}}return out;});
    }

    public CompletableFuture<Void> linkCase(int caseId,String type,int recordId) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_case_links(case_id,record_type,record_id,created_at) VALUES(?,?,?,?)")){ps.setInt(1,caseId);ps.setString(2,type.toUpperCase(Locale.ROOT));ps.setInt(3,recordId);ps.setLong(4,System.currentTimeMillis());ps.executeUpdate();}});
    }

    public CompletableFuture<List<CaseLink>> caseLinks(int caseId) {
        return supply(c -> {List<CaseLink> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT id,record_type,record_id,created_at FROM smp_case_links WHERE case_id=? ORDER BY id")){ps.setInt(1,caseId);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new CaseLink(rs.getInt(1),rs.getString(2),rs.getInt(3),rs.getLong(4)));}}return out;});
    }

    public CompletableFuture<Integer> createBounty(UUID target,String targetName,UUID creator,String creatorName,String material,int amount) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_bounties(target_uuid,target_name,creator_uuid,creator_name,material,amount,status,created_at) VALUES(?,?,?,?,?,?,'ACTIVE',?)",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,target.toString());ps.setString(2,targetName);ps.setString(3,creator.toString());ps.setString(4,creatorName);ps.setString(5,material);ps.setInt(6,amount);ps.setLong(7,System.currentTimeMillis());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){return rs.next()?rs.getInt(1):-1;}}});
    }

    public CompletableFuture<List<BountyRow>> activeBounties(UUID target) {
        return supply(c -> {List<BountyRow> out=new ArrayList<>();String sql="SELECT id,target_uuid,target_name,creator_uuid,creator_name,material,amount,created_at FROM smp_bounties WHERE status='ACTIVE'"+(target==null?"":" AND target_uuid=?")+" ORDER BY created_at DESC LIMIT 200";try(PreparedStatement ps=c.prepareStatement(sql)){if(target!=null)ps.setString(1,target.toString());try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(readBounty(rs));}}return out;});
    }

    public CompletableFuture<Boolean> claimBounty(int id,UUID killer,String killerName) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_bounties SET status='CLAIMED',claimed_at=?,claimed_by_uuid=?,claimed_by_name=? WHERE id=? AND status='ACTIVE'")){ps.setLong(1,System.currentTimeMillis());ps.setString(2,killer.toString());ps.setString(3,killerName);ps.setInt(4,id);return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Optional<BountyRow>> cancelBounty(int id,UUID creator,boolean staff) {
        return supply(c -> {
            String sql="SELECT id,target_uuid,target_name,creator_uuid,creator_name,material,amount,created_at FROM smp_bounties WHERE id=? AND status='ACTIVE'"+(staff?"":" AND creator_uuid=?");
            try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,id);if(!staff)ps.setString(2,creator.toString());try(ResultSet rs=ps.executeQuery()){if(!rs.next())return Optional.empty();BountyRow row=readBounty(rs);try(PreparedStatement up=c.prepareStatement("UPDATE smp_bounties SET status='CANCELLED' WHERE id=?")){up.setInt(1,id);up.executeUpdate();}return Optional.of(row);}}
        });
    }

    public CompletableFuture<Long> incrementCounter(UUID uuid,String key,long amount) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_counters(uuid,counter_key,value) VALUES(?,?,?) ON CONFLICT(uuid,counter_key) DO UPDATE SET value=value+excluded.value")){ps.setString(1,uuid.toString());ps.setString(2,key);ps.setLong(3,amount);ps.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("SELECT value FROM smp_counters WHERE uuid=? AND counter_key=?")){ps.setString(1,uuid.toString());ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0L;}}});
    }

    public CompletableFuture<Long> counter(UUID uuid,String key) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT value FROM smp_counters WHERE uuid=? AND counter_key=?")){ps.setString(1,uuid.toString());ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0L;}}});
    }

    public CompletableFuture<Long> incrementQuest(UUID uuid,String quest,String period,long amount) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_quest_progress(uuid,quest_id,period_key,progress,claimed) VALUES(?,?,?,?,0) ON CONFLICT(uuid,quest_id,period_key) DO UPDATE SET progress=progress+excluded.progress")){ps.setString(1,uuid.toString());ps.setString(2,quest);ps.setString(3,period);ps.setLong(4,amount);ps.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("SELECT progress FROM smp_quest_progress WHERE uuid=? AND quest_id=? AND period_key=?")){ps.setString(1,uuid.toString());ps.setString(2,quest);ps.setString(3,period);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0L;}}});
    }

    public CompletableFuture<QuestProgress> questProgress(UUID uuid,String quest,String period) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT progress,claimed FROM smp_quest_progress WHERE uuid=? AND quest_id=? AND period_key=?")){ps.setString(1,uuid.toString());ps.setString(2,quest);ps.setString(3,period);try(ResultSet rs=ps.executeQuery()){return rs.next()?new QuestProgress(rs.getLong(1),rs.getInt(2)!=0):new QuestProgress(0,false);}}});
    }

    public CompletableFuture<Boolean> claimQuest(UUID uuid,String quest,String period) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_quest_progress SET claimed=1 WHERE uuid=? AND quest_id=? AND period_key=? AND claimed=0")){ps.setString(1,uuid.toString());ps.setString(2,quest);ps.setString(3,period);return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Boolean> unlockAchievement(UUID uuid,String id) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT OR IGNORE INTO smp_achievements(uuid,achievement_id,unlocked_at) VALUES(?,?,?)")){ps.setString(1,uuid.toString());ps.setString(2,id);ps.setLong(3,System.currentTimeMillis());return ps.executeUpdate()>0;}});
    }

    public CompletableFuture<Set<String>> achievements(UUID uuid) {
        return supply(c -> {Set<String> out=new LinkedHashSet<>();try(PreparedStatement ps=c.prepareStatement("SELECT achievement_id FROM smp_achievements WHERE uuid=? ORDER BY unlocked_at")){ps.setString(1,uuid.toString());try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(rs.getString(1));}}return out;});
    }

    public CompletableFuture<Void> logStaff(UUID uuid,String name,String type,String detail) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_staff_activity(staff_uuid,staff_name,action_type,detail,created_at) VALUES(?,?,?,?,?)")){if(uuid==null)ps.setNull(1,Types.VARCHAR);else ps.setString(1,uuid.toString());ps.setString(2,name);ps.setString(3,type);ps.setString(4,detail);ps.setLong(5,System.currentTimeMillis());ps.executeUpdate();}});
    }

    public CompletableFuture<StaffStats> staffStats(UUID uuid) {
        return supply(c -> {int commands=0,punishments=0,reports=0,cases=0;try(PreparedStatement ps=c.prepareStatement("SELECT action_type,COUNT(*) c FROM smp_staff_activity WHERE staff_uuid=? GROUP BY action_type")){ps.setString(1,uuid.toString());try(ResultSet rs=ps.executeQuery()){while(rs.next()){String t=rs.getString(1);int n=rs.getInt(2);if(t.equals("COMMAND"))commands+=n;else if(t.equals("PUNISHMENT"))punishments+=n;else if(t.equals("REPORT"))reports+=n;else if(t.equals("CASE"))cases+=n;}}}return new StaffStats(commands,punishments,reports,cases);});
    }

    public CompletableFuture<Integer> addUndo(UUID actor,String actorName,String type,String payload) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_undo(actor_uuid,actor_name,action_type,payload,created_at) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){if(actor==null)ps.setNull(1,Types.VARCHAR);else ps.setString(1,actor.toString());ps.setString(2,actorName);ps.setString(3,type);ps.setString(4,payload);ps.setLong(5,System.currentTimeMillis());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){return rs.next()?rs.getInt(1):-1;}}});
    }

    public CompletableFuture<List<UndoRow>> undoRows(int limit) {
        return supply(c -> {List<UndoRow> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT id,actor_name,action_type,payload,created_at,undone FROM smp_undo ORDER BY id DESC LIMIT ?")){ps.setInt(1,limit);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new UndoRow(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6)!=0));}}return out;});
    }

    public CompletableFuture<Optional<UndoRow>> undoRow(int id) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT id,actor_name,action_type,payload,created_at,undone FROM smp_undo WHERE id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(new UndoRow(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6)!=0)):Optional.empty();}}});
    }

    public CompletableFuture<Void> markUndone(int id) { return run(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_undo SET undone=1,undone_at=? WHERE id=?")){ps.setLong(1,System.currentTimeMillis());ps.setInt(2,id);ps.executeUpdate();}}); }

    public CompletableFuture<Void> startSeason(String id,String display) {
        return run(c -> {try(PreparedStatement stop=c.prepareStatement("UPDATE smp_seasons SET status='ENDED',ended_at=? WHERE status='ACTIVE'")){stop.setLong(1,System.currentTimeMillis());stop.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_seasons(id,display_name,status,started_at) VALUES(?,?,'ACTIVE',?) ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,status='ACTIVE',started_at=excluded.started_at,ended_at=NULL")){ps.setString(1,id);ps.setString(2,display);ps.setLong(3,System.currentTimeMillis());ps.executeUpdate();}});
    }

    public CompletableFuture<Void> endSeason() { return run(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE smp_seasons SET status='ENDED',ended_at=? WHERE status='ACTIVE'")){ps.setLong(1,System.currentTimeMillis());ps.executeUpdate();}}); }

    public CompletableFuture<Optional<SeasonRow>> activeSeason() {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT id,display_name,started_at FROM smp_seasons WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1");ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(new SeasonRow(rs.getString(1),rs.getString(2),rs.getLong(3))):Optional.empty();}});
    }

    public CompletableFuture<Void> addSeasonStat(String season,UUID uuid,String name,String column,long amount) {
        if(!Set.of("kills","deaths","chat_wins","playtime_ms").contains(column))throw new IllegalArgumentException("Invalid season stat");
        return run(c -> {try(PreparedStatement ensure=c.prepareStatement("INSERT OR IGNORE INTO smp_season_stats(season_id,uuid,name) VALUES(?,?,?)")){ensure.setString(1,season);ensure.setString(2,uuid.toString());ensure.setString(3,name);ensure.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("UPDATE smp_season_stats SET name=?,"+column+"="+column+"+? WHERE season_id=? AND uuid=?")){ps.setString(1,name);ps.setLong(2,amount);ps.setString(3,season);ps.setString(4,uuid.toString());ps.executeUpdate();}});
    }

    public CompletableFuture<List<SeasonStat>> seasonTop(String season,String metric,int limit) {
        String order=switch(metric){case "kills"->"kills DESC";case "deaths"->"deaths DESC";case "chat_wins"->"chat_wins DESC";case "playtime"->"playtime_ms DESC";case "kd"->"CASE WHEN deaths=0 THEN kills ELSE CAST(kills AS REAL)/deaths END DESC";default->"kills DESC";};
        return supply(c -> {List<SeasonStat> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT uuid,name,kills,deaths,chat_wins,playtime_ms FROM smp_season_stats WHERE season_id=? ORDER BY "+order+" LIMIT ?")){ps.setString(1,season);ps.setInt(2,limit);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new SeasonStat(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getLong(6)));}}return out;});
    }

    public CompletableFuture<Void> logCombat(UUID killer,String killerName,UUID victim,String victimName,String weapon,double distance,String assists,boolean revenge) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_combat_log(killer_uuid,killer_name,victim_uuid,victim_name,weapon,distance,assists,revenge,created_at) VALUES(?,?,?,?,?,?,?,?,?)")){if(killer==null)ps.setNull(1,Types.VARCHAR);else ps.setString(1,killer.toString());ps.setString(2,killerName);ps.setString(3,victim.toString());ps.setString(4,victimName);ps.setString(5,weapon);ps.setDouble(6,distance);ps.setString(7,assists);ps.setInt(8,revenge?1:0);ps.setLong(9,System.currentTimeMillis());ps.executeUpdate();}});
    }

    public CompletableFuture<CombatStats> combatStats(UUID uuid) {
        return supply(c -> {int kills=0,deaths=0,revenge=0;double distance=0;try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*),COALESCE(MAX(distance),0),COALESCE(SUM(revenge),0) FROM smp_combat_log WHERE killer_uuid=?")){ps.setString(1,uuid.toString());try(ResultSet rs=ps.executeQuery()){if(rs.next()){kills=rs.getInt(1);distance=rs.getDouble(2);revenge=rs.getInt(3);}}}try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM smp_combat_log WHERE victim_uuid=?")){ps.setString(1,uuid.toString());try(ResultSet rs=ps.executeQuery()){if(rs.next())deaths=rs.getInt(1);}}return new CombatStats(kills,deaths,revenge,distance);});
    }

    public CompletableFuture<Boolean> rulesAccepted(UUID uuid) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT rules_accepted FROM smp_onboarding WHERE uuid=?")){ps.setString(1,uuid.toString());try(ResultSet rs=ps.executeQuery()){return rs.next()&&rs.getInt(1)!=0;}}});
    }

    public CompletableFuture<Void> setRulesAccepted(UUID uuid,boolean accepted) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_onboarding(uuid,rules_accepted,completed_at) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET rules_accepted=excluded.rules_accepted,completed_at=excluded.completed_at")){ps.setString(1,uuid.toString());ps.setInt(2,accepted?1:0);if(accepted)ps.setLong(3,System.currentTimeMillis());else ps.setNull(3,Types.BIGINT);ps.executeUpdate();}});
    }

    public CompletableFuture<Long> lastScheduleRun(String id) {
        return supply(c -> {try(PreparedStatement ps=c.prepareStatement("SELECT last_run FROM smp_schedule_runs WHERE job_id=?")){ps.setString(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0L;}}});
    }

    public CompletableFuture<Void> markScheduleRun(String id,long time) {
        return run(c -> {try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_schedule_runs(job_id,last_run) VALUES(?,?) ON CONFLICT(job_id) DO UPDATE SET last_run=excluded.last_run")){ps.setString(1,id);ps.setLong(2,time);ps.executeUpdate();}});
    }

    private static CaseRow readCase(ResultSet rs)throws SQLException{long closed=rs.getLong(9);return new CaseRow(rs.getInt(1),UUID.fromString(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getLong(8),rs.wasNull()?null:closed);}
    private static BountyRow readBounty(ResultSet rs)throws SQLException{return new BountyRow(rs.getInt(1),UUID.fromString(rs.getString(2)),rs.getString(3),UUID.fromString(rs.getString(4)),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getLong(8));}

    public record PunishmentRow(int id,String reason,String staffName,long createdAt,boolean active){}
    public record ReportRow(int id,String reporterName,String targetName,String reason,String status,String staffName,long createdAt,String result){}
    public record CaseRow(int id,UUID targetUuid,String targetName,String title,String status,String staffName,long createdAt,long updatedAt,Long closedAt){}
    public record CaseComment(int id,String staffName,String kind,String body,long createdAt){}
    public record CaseLink(int id,String type,int recordId,long createdAt){}
    public record BountyRow(int id,UUID targetUuid,String targetName,UUID creatorUuid,String creatorName,String material,int amount,long createdAt){}
    public record QuestProgress(long progress,boolean claimed){}
    public record StaffStats(int commands,int punishments,int reports,int cases){}
    public record UndoRow(int id,String actorName,String type,String payload,long createdAt,boolean undone){}
    public record SeasonRow(String id,String displayName,long startedAt){}
    public record SeasonStat(UUID uuid,String name,int kills,int deaths,int chatWins,long playtimeMs){public double kd(){return deaths==0?kills:(double)kills/deaths;}}
    public record CombatStats(int kills,int deaths,int revengeKills,double longestKillDistance){}

    @FunctionalInterface private interface SqlRunnable{void run(Connection c)throws SQLException;}
    @FunctionalInterface private interface SqlSupplier<T>{T get(Connection c)throws SQLException;}
}
