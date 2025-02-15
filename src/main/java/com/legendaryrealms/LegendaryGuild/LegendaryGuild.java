package com.legendaryrealms.LegendaryGuild;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.collect.Iterables;
import com.legendaryrealms.LegendaryGuild.API.LegendaryGuildPlaceholderAPI;
import com.legendaryrealms.LegendaryGuild.API.UserAPI;
import com.legendaryrealms.LegendaryGuild.Data.Guild.Guild;
import com.legendaryrealms.LegendaryGuild.Data.User.Position;
import com.legendaryrealms.LegendaryGuild.Data.User.User;
import com.legendaryrealms.LegendaryGuild.Hook.Sub.PlaceholderAPIHook;
import com.legendaryrealms.LegendaryGuild.Listener.*;
import com.legendaryrealms.LegendaryGuild.Listener.Custom.NewCycleEvent;
import com.legendaryrealms.LegendaryGuild.Manager.Guild.*;
import com.legendaryrealms.LegendaryGuild.Manager.Others.*;
import com.legendaryrealms.LegendaryGuild.Utils.BungeeCord.NetWork;
import com.legendaryrealms.LegendaryGuild.Utils.BungeeCord.NetWorkHandle;
import com.legendaryrealms.LegendaryGuild.Command.Commands;
import com.legendaryrealms.LegendaryGuild.Files.Lang;
import com.legendaryrealms.LegendaryGuild.Data.Database.DataProvider;
import com.legendaryrealms.LegendaryGuild.Data.Database.MysqlStore;
import com.legendaryrealms.LegendaryGuild.Data.Database.SqliteStore;
import com.legendaryrealms.LegendaryGuild.Manager.*;
import com.legendaryrealms.LegendaryGuild.Manager.User.PositionsManager;
import com.legendaryrealms.LegendaryGuild.Manager.User.UsersManager;
import com.legendaryrealms.LegendaryGuild.Utils.MsgUtils;
import com.legendaryrealms.LegendaryGuild.Utils.UpdateCheck;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LegendaryGuild extends JavaPlugin implements PluginMessageListener {
    public boolean version_high;
    private static List<String> players;
    private static LegendaryGuild legendaryGuild;
    public Lang.LangType lang = Lang.LangType.Chinese;
    public String SERVER = "Server";

    @Override
    public void onEnable() {

        long time = System.currentTimeMillis();

        players = new ArrayList<>();
        legendaryGuild = this;
        scheduler = UniversalScheduler.getScheduler(this);
        //获取是否高版本
        version_high = BukkitVersionHigh();

        //初始化基础模块
        initEssentails();

        //创建数据库
        loadDatabase();

        //加载数据
        reloadData();

        //注册指令
        Bukkit.getPluginCommand("legendaryguild").setExecutor(new Commands());
        Bukkit.getPluginCommand("legendaryguild").setTabCompleter(new Commands());
        Commands.register();

        //注册监听器
        registerListener();

        //注册跨服同步
        netWork = new NetWorkHandle(this);

        chatControl = new ChatEvent(this);

        //加载所有公会到缓存中
        sync(new Runnable() {
            @Override
            public void run() {
                guildsManager.loadGuilds();
                if (fileManager.getStores().isEnable()){
                    storesManager.loadStores();
                }
            }
        });

        //获取全服在线玩家
        //减少玩家公会创建/加入冷却
        sync(new Runnable() {
            @Override
            public void run() {
                if (netWork.isEnable()) {
                    Player p = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
                    if (p != null) {
                        netWork.PlayerList(p);
                    }
                }

                Bukkit.getOnlinePlayers().forEach(p -> {
                    User user = UserAPI.getUser(p.getName());
                    if (user.getCooldown() > 0){
                        user.setCooldown( user.getCooldown() - 1);
                    }
                });
            }
        },20,20);

        //检测周期
        sync(new Runnable() {
            @Override
            public void run() {
                checkDate();
            }
        },20,200);

        Bukkit.getConsoleSender().sendMessage(fileManager.getLang().plugin+msgUtils.msg("&a插件启动成功！ 耗时&e"+(System.currentTimeMillis()-time) +"ms"));

        Metrics metrics = new Metrics(this, 19359);

        //注册变量
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
          legendaryGuildPlaceholderAPI =  new LegendaryGuildPlaceholderAPI();
        }

        //更新检测
        updateCheck();
    }
    private LegendaryGuildPlaceholderAPI legendaryGuildPlaceholderAPI;
    @Override
    public void onDisable() {
        //断开数据库连接
        dataProvider.closeDataBase();
        netWork.disable();
        if (legendaryGuildPlaceholderAPI != null) {
            legendaryGuildPlaceholderAPI.unregister();
        }
     }
    public static void setPlayers(List<String> player){
        players = player;
     }
    public void initEssentails(){

        msgUtils = new MsgUtils(this);
        positionsManager = new PositionsManager(this);
        fileManager = new FileManager();
        requirementsManager = new RequirementsManager(this);
        hookManager = new HookManager(this);
        createGroupsManager = new CreateGroupsManager(this);
        waterPotsManager = new WaterPotsManager(this);
        guildIconsManager = new GuildIconsManager(this);
        tributesItemsManager = new TributesItemsManager(this);
        guildShopItemsManager = new GuildShopItemsManager(this);
        if (fileManager.getBuffFile().getEnable()){
            buffsManager = new BuffsManager(this);
        }
        activityRewardsManager = new ActivityRewardsManager(this);
        menuLoadersManager = new MenuLoadersManager(this);
        teamShopManager = new TeamShopManager(this);
    }

    public void reloadData(){

        usersManager = new UsersManager(this);
        guildsManager = new GuildsManager(this);
        redPacketsManager = new GuildRedPacketsManager(this);
        guildShopDataManager = new GuildShopDataManager(this);
        if (fileManager.getStores().isEnable()) {
            storesManager = new GuildStoresManager(this);
        }
        guildActivityDataManager = new GuildActivityDataManager(this);
        guildTeamShopDataManager = new GuildTeamShopDataManager(this);

    }

    private void registerListener(){
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(),this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuit(),this);
        Bukkit.getPluginManager().registerEvents(new MenuEvent(),this);
        Bukkit.getPluginManager().registerEvents(new PvpEvent(),this);
        Bukkit.getPluginManager().registerEvents(new NewCycle(),this);
        Bukkit.getPluginManager().registerEvents(new MoveEvent(),this);

    }

    private void updateCheck(){
        scheduler.runTaskLaterAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                new UpdateCheck(legendaryGuild,114036).getVersion(v ->{
                    if (legendaryGuild.getDescription().getVersion().equals(v)) {
                        getLogger().info("There is not a new update available.");
                        getLogger().info(color("&a当前使用版本为最新版本"));

                    } else {
                        getLogger().info("There is a new update available.");
                        Bukkit.getConsoleSender().sendMessage(color("当前使用版本: &c"+legendaryGuild.getDescription().getVersion()));
                        Bukkit.getConsoleSender().sendMessage(color("最新版本: &c"+v));
                        Bukkit.getConsoleSender().sendMessage(color("请前往 &a&nhttps://www.spigotmc.org/resources/legendaryguild-%E2%9C%A8-a-rich-and-powerful-guild-system.114036/ &f获取最新版本."));
                    }
                });
            }
        },200);

    }

    //注册BC通讯通道
    public void registerBungeecordChannel(){
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }
    public void unregisterBungeecordChannel(){
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        netWork.recive(s,player,bytes);
    }



    public void setServerName(String name){
        this.SERVER = name;
    }
    public static LegendaryGuild getInstance(){return legendaryGuild;}
    public HookManager getHookManager() {
        return hookManager;
    }
    public DataProvider getDataBase() {
        return dataProvider;
    }

    public GuildsManager getGuildsManager() {
        return guildsManager;
    }

    public ChatEvent getChatControl() {
        return chatControl;
    }

    public FileManager getFileManager() {
        return fileManager;
    }
    public UsersManager getUsersManager() {
        return usersManager;
    }
    public RequirementsManager getRequirementsManager(){return requirementsManager;}

    public CreateGroupsManager getCreateGroupsManager() {
        return createGroupsManager;
    }

    public static List<String> getPlayers() {
        return players;
    }

    public NetWork getNetWork() {
        return netWork;
    }

    public MsgUtils getMsgUtils() {
        return msgUtils;
    }

    public PositionsManager getPositionsManager() {
        return positionsManager;
    }

    public GuildStoresManager getStoresManager() {
        return storesManager;
    }

    public MenuLoadersManager getMenuLoadersManager() {
        return menuLoadersManager;
    }

    public GuildRedPacketsManager getRedPacketsManager() {
        return redPacketsManager;
    }

    public WaterPotsManager getWaterPotsManager() {return waterPotsManager;}

    public GuildIconsManager getGuildIconsManager() {
        return guildIconsManager;
    }

    public TributesItemsManager getTributesItemsManager() {
        return tributesItemsManager;
    }

    public GuildShopDataManager getGuildShopDataManager() {
        return guildShopDataManager;
    }

    public GuildShopItemsManager getGuildShopItemsManager() {
        return guildShopItemsManager;
    }

    public BuffsManager getBuffsManager() {
        return buffsManager;
    }

    public GuildActivityDataManager getGuildActivityDataManager() {
        return guildActivityDataManager;
    }

    public ActivityRewardsManager getActivityRewardsManager() {
        return activityRewardsManager;
    }

    public TeamShopManager getTeamShopManager() {
        return teamShopManager;
    }

    public GuildTeamShopDataManager getGuildTeamShopDataManager() {
        return guildTeamShopDataManager;
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

    private TaskScheduler scheduler;
    private ActivityRewardsManager activityRewardsManager;
    private MsgUtils msgUtils;
    private GuildsManager guildsManager;
    private NetWork netWork;
    private DataProvider dataProvider;
    private FileManager fileManager;
    private RequirementsManager requirementsManager;
    private HookManager hookManager;
    private CreateGroupsManager createGroupsManager;
    private UsersManager usersManager;
    private PositionsManager positionsManager;
    private MenuLoadersManager menuLoadersManager;
    private GuildStoresManager storesManager;
    private GuildRedPacketsManager redPacketsManager;
    private WaterPotsManager waterPotsManager;
    private GuildIconsManager guildIconsManager;
    private TributesItemsManager tributesItemsManager;
    private GuildShopDataManager guildShopDataManager;
    private GuildShopItemsManager guildShopItemsManager;
    private BuffsManager buffsManager;
    private GuildActivityDataManager guildActivityDataManager;
    private ChatEvent chatControl;
    private TeamShopManager teamShopManager;
    private GuildTeamShopDataManager guildTeamShopDataManager;
    private void loadDatabase(){
        DataProvider.DatabaseType type = fileManager.getConfig().store;
        switch (type){
            case MYSQL:
                dataProvider = new MysqlStore(this);
                break;
            case SQLite:
                dataProvider = new SqliteStore(this);
                break;
        }
    }
    private boolean BukkitVersionHigh() {
        String name = Bukkit.getServer().getBukkitVersion();
        String versionStr =  name.substring(0,name.indexOf("-"));

        List<String> groups = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (char c : versionStr.toCharArray()) {
            if (c == '.') {
                groups.add(builder.toString());
                builder = new StringBuilder();
                continue;
            }
            builder.append(c);
        }
        groups.add(builder.toString());

        int version = Integer.parseInt(groups.get(1));
        return (version >= 13);
    }
    public String color(String msg){return msgUtils.msg(msg);}
    public List<String> color(List<String> msg){return msgUtils.msg(msg);}
    public void info(String msg, Level level,Throwable throwable){
        getLogger().log(level,msg,throwable);
    }
    public void info(String msg, Level level){
        getLogger().log(level,msg);
    }
    public List<String> getOnlinePlayersName(){
        return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList());
    }
    public boolean checkIsNumber(String arg) {
        Pattern pattern = Pattern.compile("[0-9]+[.]{0,1}[0-9]*[dD]{0,1}");
        Matcher isNum = pattern.matcher(arg);
        return isNum.matches();
    }
    public void sync(Runnable consumer){
        scheduler.runTaskAsynchronously(legendaryGuild,consumer);
    }
    public void sync(Runnable runnable,int delay){
        scheduler.runTaskLaterAsynchronously(legendaryGuild,runnable,delay);
    }
    public void sync(Runnable runnable,int delay,int timer){
        scheduler.runTaskTimerAsynchronously(legendaryGuild,runnable,delay,timer);
    }
    public String getDate(){
        SimpleDateFormat df= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = df.format(System.currentTimeMillis());
        return date;
    }
    private void checkDate(){
        if (getFileManager().getConfig().checkData) {
            int day = Integer.parseInt(dataProvider.getSystemData("last_date").orElse("0"));
            int week = Integer.parseInt(dataProvider.getSystemData("last_week").orElse("0"));
            int month = Integer.parseInt(dataProvider.getSystemData("last_month").orElse("0"));

            Calendar calendar = Calendar.getInstance();
            int today = calendar.get(Calendar.DATE);
            int thisWeek = calendar.get(Calendar.WEEK_OF_MONTH);
            int thisMonth = calendar.get(Calendar.MONTH);

            if (day != today) {
                dataProvider.saveSystemData("last_date", today + "");
                scheduler.runTask(this, () -> Bukkit.getPluginManager().callEvent(new NewCycleEvent(0, today)));

            }
            if (week != thisWeek) {
                dataProvider.saveSystemData("last_week", thisWeek + "");
                scheduler.runTask(this, () -> Bukkit.getPluginManager().callEvent(new NewCycleEvent(1, thisWeek)));
            }
            if (month != thisMonth) {
                dataProvider.saveSystemData("last_month", thisMonth + "");
                scheduler.runTask(this, () -> Bukkit.getPluginManager().callEvent(new NewCycleEvent(2, thisMonth)));
            }
        }
    }
}
