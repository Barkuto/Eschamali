package modules;

import base.Command;
import base.EschaUtil;
import base.Module;
import discord4j.core.DiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import pad.data.PADData;
import pad.data.structure.card.*;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Created by Iggie on 8/25/2016.
 */
public class PAD extends Module {
    private TreeMap<String, String> abbrMon = new TreeMap<>();
    private TreeMap<String, String> abbrDun = new TreeMap<>();
    private int maxMonNum = 5636;

    private String tableName = "pad";
    private String col1 = "field";
    private String col2 = "value";
    private String[] tableCols = {col1, col2};

    private String guerillasField = "guerillas";

    private String emoteServerFile = "modules/PAD/emoteserver.txt";
    private ArrayList<Guild> emoteServers = new ArrayList<>();
    private boolean useEmotes = false;

    private Pattern p1 = Pattern.compile("^&(buttoncalc|bc) (\\d+)\\s*(\\d+)?\\s*(\\d+)?\\s*(\\d+)?\\s*([TtYyFfNn])?\\s*(\\d+)?\\s*(\\d+)?\\s*$");
    private Pattern p2 = Pattern.compile("\\d+");
    private Pattern p3 = Pattern.compile(" [TtYyFfNn]");

    private boolean threadRunning = false;
    private boolean updatingDB = false;

    private PADData paddata;

    public PAD(DiscordClient client, String credentials) {
        super(client, "&");
        paddata = new PADData(credentials);
        abbrMon.put("mzeus", "awoken machine zeus");
        abbrMon.put("mhera", "awoken machine hera");
        abbrMon.put("miru", "star myr");
        abbrMon.put("myr", "star myr");
        abbrMon.put("z8", "maleficent phantom zaerog");
        abbrMon.put("radra", "sun god ra dragon");
        abbrMon.put("ra dragon", "sun god ra dragon");
        abbrMon.put("ragdrag", "anti god machine ragnarok");
        abbrMon.put("rag drag", "anti god machine ragnarok");
        abbrMon.put("rag dragon", "anti god machine ragnarok");

        abbrDun.put("sudr", "super ultimate dragon rush");
        abbrDun.put("mhera", "machine hera descended");
        abbrDun.put("mzeus", "machine zeus descended");
        abbrDun.put("z8", "zaerog descended");

        // Load Emote Servers
        client.getEventDispatcher().on(ReadyEvent.class).flatMap(event -> {
            File f = new File(emoteServerFile);
            try {
                Scanner s = new Scanner(f);
                String[] line = s.nextLine().split(";");
                for (int i = 0; i < line.length; i++) {
                    Guild g = client.getGuildById(Snowflake.of(Long.parseLong(line[i]))).block();
                    if (g != null)
                        emoteServers.add(g);
                }

                if (emoteServers.size() > 0)
                    useEmotes = true;

                s.close();

                AwakeningEmoji.loadEmojis(emoteServers);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return Mono.empty();
        }).subscribe();

        client.getEventDispatcher().on(GuildCreateEvent.class).flatMap(event -> {
            Guild guild = event.getGuild();
            DBDriver driver = ChannelPerms.getPermissionDB(guild);
            if (!driver.tableExists(tableName)) {
                driver.createTable(tableName, tableCols, new String[]{"string", "string"}, false);
            }
            driver.close();
            return Mono.empty();
        }).subscribe();
    }

    @Override
    protected Map<String, Command> makeCommands() {
        Map<String, Command> commands = new HashMap<>();

        Command update = event -> {
            Guild guild = event.getGuild().block();
            MessageChannel channel = event.getMessage().getChannel().block();
            if (ChannelPerms.canModuleIn(guild, getName(), channel)) {
                if (!updatingDB) {
                    updatingDB = true;
                    EschaUtil.sendMessage(event, "Updating DB. Might take a while.").subscribe();
                    new Thread(() -> {
                        paddata.updateMonsters();
                        EschaUtil.sendMessage(channel, "DB updated.").subscribe();
                        updatingDB = false;
                    }).start();
                    return Mono.empty();
                } else return EschaUtil.sendMessage(channel, "Already updating, please wait.");
            }
            return Mono.empty();
        };

        Command info = event -> {
            Guild guild = event.getGuild().block();
            MessageChannel channel = event.getMessage().getChannel().block();
            if (ChannelPerms.canModuleIn(guild, getName(), channel)) {
                String cmd = event.getMessage().getContent().get().split(" ")[0].replace(prefix, "");
                String[] args = EschaUtil.getArgs(event);
                String query = EschaUtil.getArgsConcat(event);
                String region = "NA";
                if (cmd.equalsIgnoreCase("infojp") || cmd.equalsIgnoreCase("ij"))
                    region = "JP";

                Monster monster = null;
                if (args.length == 0) {
                    monster = paddata.getMonster(new Random().nextInt(maxMonNum) + 1 + "", region);
                } else {
                    monster = paddata.getMonster(query, region);
                    if (region.equals("NA") && monster == null) {
                        monster = paddata.getMonster(query, "JP");
                        region = "JP";
                    }
                }

                if (monster != null) {
                    Monster m = monster;
                    String fRegion = region;
                    Mono<Message> msg = channel.createMessage(mSpec -> mSpec.setEmbed(e -> {
                        Color c = Color.GRAY;
                        switch (Attribute.fromID(m.getAttr_id())) {
                            case FIRE:
                                c = new Color(0xff744b);
                                break;
                            case WATER:
                                c = new Color(0x40ffff);
                                break;
                            case WOOD:
                                c = new Color(0x4cd962);
                                break;
                            case LIGHT:
                                c = new Color(0xf2e74c);
                                break;
                            case DARK:
                                c = new Color(0xcc54c2);
                                break;
                        }
                        String desc = "";
                        int[] awakenings = m.getAwakenings();
                        for (int i = 0; i < awakenings.length; i++) {
                            if (useEmotes) {
                                desc += AwakeningEmoji.getEmoji(awakenings[i]).asFormat();
                            } else {
                                desc += Awakening.values()[awakenings[i]].getShortName();
                                if (i != awakenings.length - 1) {
                                    desc += "║";
                                }
                            }
                        }
                        if (desc.length() == 0)
                            desc += "No Awakenings.";

                        int[] superAwakenings = m.getSuper_awakenings();
                        if (superAwakenings.length > 0)
                            desc += "\n";
                        for (int i = 0; i < superAwakenings.length; i++) {
                            if (useEmotes) {
                                desc += AwakeningEmoji.getEmoji(superAwakenings[i]).asFormat();
                            } else {
                                desc += Awakening.values()[superAwakenings[i]].getShortName();
                                if (i != superAwakenings.length - 1) {
                                    desc += "║";
                                }
                            }
                        }

                        TreeSet<Type> validKillers = m.getValidKillerLatents();
                        if (validKillers.size() > 0) {
                            desc += "\nKillers: ";
                            if (validKillers.size() == 8)
                                desc += "Any";
                            else {
                                StringBuilder sb = new StringBuilder();
                                for (Type t : m.getValidKillerLatents()) {
                                    sb.append(t.getName()).append(" ");
                                }
                                desc += sb.toString();
                            }
                        }
                        e.setDescription("**" + desc + "**");

                        Type type = Type.fromID(m.getType_1_id());
                        Type type2 = Type.fromID(m.getType_2_id());
                        Type type3 = Type.fromID(m.getType_3_id());
                        String inheritable = m.isInheritable() ? "Yes" : "No";
                        String typing = type.getName() + (type2 == Type.NONE ? "" : "/" + type2.getName()) + (type3 == Type.NONE ? "" : "/" + type3.getName()) + "\n";
                        String mInfo = String.format("**Rarity** %-5d" + "\n**Cost**   %-5d" + "\n**MP**     %-5d" + "\n**Inheritable** %-5s", m.getRarity(), m.getCost(), m.getSell_mp(), inheritable);
                        e.addField(typing, mInfo, true);

                        int hp = m.getMax_hp();
                        int atk = m.getMax_atk();
                        int rcv = m.getMax_rcv();
                        int weighted = m.getWeighted();

                        if (m.getLimit_mult() == 0)
                            e.addField("**Weighted** " + weighted, String.format("**HP**    %-4d\n**ATK** %-4d\n**RCV** %-4d", hp, atk, rcv), true);
                        else {
                            int lbhp = m.getLB_hp();
                            int lbatk = m.getLB_atk();
                            int lbrcv = m.getLB_rcv();
                            int lbweighted = m.getLB_weighted();
                            e.addField("**Weighted** " + weighted + " | " + "**LB** " + lbweighted,
                                    String.format("**HP**    %-4d | %-4d\n**ATK** %-4d | %-4d\n**RCV** %-4d | %-4d", hp, lbhp, atk, lbatk, rcv, lbrcv), true);
                        }


                        ActiveSkill active = m.getActive_Skill();
                        String activeName = "Active: " + (active == null ? "" : active.getName() + " (" + active.getTurn_max() + "->" + active.getTurn_min() + ")");
                        e.addField(activeName, active == null ? "None." : active.getClean_description(), false);

                        LeaderSkill leader = m.getLeader_Skill();
                        String leaderName = "Leader: " + (leader == null ? "" : leader.getName());
                        e.addField(leaderName, leader == null ? "None." : leader.getClean_description().replace("^p", "").replace(";", ""), false);

                        int[] evos = m.getEvolutions();
                        TreeSet<Integer> otherEvoes = new TreeSet<>(Integer::compareTo);
                        for (int i = 0; i < evos.length; i++) {
                            if (evos[i] != m.getCard_id())
                                otherEvoes.add(evos[i]);
                        }

                        String otherEvos = otherEvoes.toString().replace("[", "").replace("]", "");

                        ArrayList<Monster> similarNames = query.length() > 0 ? paddata.getAllMonsters(query, fRegion) : new ArrayList<>();
                        String similar = "";
                        if (similarNames.size() <= 10) {
                            for (int i = 0; i < similarNames.size(); i++) {
                                int currentID = similarNames.get(i).getCard_id();
                                if (currentID != m.getCard_id()) {
                                    boolean contains = false;
                                    for (int j = 0; j < evos.length; j++) {
                                        if (evos[j] == currentID) {
                                            contains = true;
                                            break;
                                        }
                                    }
                                    if (!contains)
                                        similar += similarNames.get(i).getCard_id() + ", ";
                                }
                            }
                            if (similar.contains(",")) {
                                similar = similar.substring(0, similar.lastIndexOf(","));
                            }
                        } else {
                            similar += "Too many to show.";
                        }

                        if (otherEvos.length() > 0) e.addField("Other Evos", otherEvos, true);
                        if (similar.length() > 0) e.addField("Similar Names", similar, true);

                        e.setThumbnail(paddata.getPortraitPictureURL(m.getCard_id() + "", fRegion));
                        e.setTitle("No." + m.getCard_id() + " " + m.getName());
                        e.setUrl("http://puzzledragonx.com/en/monster.asp?n=" + m.getCard_id());
                        e.setColor(c);
                    }));
                    return msg.then();
                    // TODO addMonsterEmbedReactions(sentMsg, fRegion);
                } else return EschaUtil.sendMessage(event, "Monster not found.");
            }
            return Mono.empty();
        };

        Command pic = event -> {
            Guild guild = event.getGuild().block();
            MessageChannel channel = event.getMessage().getChannel().block();
            if (ChannelPerms.canModuleIn(guild, getName(), channel)) {
                String cmd = event.getMessage().getContent().get().split(" ")[0].replace(prefix, "");
                String args[] = EschaUtil.getArgs(event);
                String argsconcat = EschaUtil.getArgsConcat(event);

                String region = "NA";
                if (cmd.equalsIgnoreCase("picjp")) region = "JP";
                if (args.length > 1 && args[0].contains("sheen")) {
                    int roll = new Random().nextInt(100);
                    System.out.println(roll);
                    if (roll >= 95) {
                        return EschaUtil.sendMessage(event, "http://i.imgur.com/oicGMFu.png");
                    }
                }
                String url;
                if (args.length == 0)
                    url = paddata.getFullPictureURL((new Random().nextInt(maxMonNum) + 1) + "", region);
                else
                    url = paddata.getFullPictureURL(argsconcat, region);

                if (url != null)
                    return channel.createMessage(mSpec -> mSpec.setEmbed(e -> {
                        e.setImage(url);
                    })).then();
//                    return EschaUtil.sendMessage(event, new EmbedBuilder().withImage(url).build());
                else return EschaUtil.sendMessage(event, "Nothing was found.");
            }
            return Mono.empty();
        };

        commands.put("update", update);
        commands.put("updatedb", update);
        commands.put("info", info);
        commands.put("i", info);
        commands.put("infojp", info);
        commands.put("ij", info);
        commands.put("pic", pic);
        commands.put("picjp", pic);
        commands.put("p", pic);
        commands.put("pj", pic);

        return commands;
    }

    @Override
    public String getName() {
        return "PAD";
    }


//    @EventSubscriber
//    public void onMessage(MessageReceivedEvent event) {
//        if (!(event.getMessage().getChannel() instanceof IPrivateChannel)) {
//            IGuild guild = event.getMessage().getGuild();
//            IChannel channel = event.getMessage().getChannel();
//            if (PermissionsListener.isModuleOn(guild, PADModule.name)
//                    && PermissionsListener.canModuleInChannel(guild, PADModule.name, channel)) {
//                if (event.getMessage().getContent().startsWith(prefix)) {
//                    String msg = event.getMessage().getContent().toLowerCase().trim();
//                    String[] split = msg.split(" ");
//                    String cmd = split[0].replace(prefix, "");
//                    IUser user = event.getMessage().getAuthor();
//

//                    } else if (cmd.equals("dungeon") || cmd.equals("dun") || cmd.equals("d")) {
//                        Sender.sendMessage(channel, searchDungeon(msg.substring(msg.indexOf(cmd) + cmd.length() + 1)));
//                    } else if (cmd.equals("as")) {
//                        //List monsters with keyword in active?
//                        //List monsters with that active "type"?
//                    } else if (cmd.equals("guerilla") || cmd.equals("g")) {
//                        Guerilla g = paddata.getTodayGuerilla();
//                        if (split.length == 1) {
//                            try {
//                                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                                ImageIO.write(paddata.guerillaToImage(g, "pst"), "png", os);
//                                InputStream is = new ByteArrayInputStream(os.toByteArray());
//                                channel.sendFile("", false, is, "img.png");
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        } else if (split.length == 2) {
//                            try {
//                                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                                ImageIO.write(paddata.guerillaToImage(g, split[1].trim()), "png", os);
//                                InputStream is = new ByteArrayInputStream(os.toByteArray());
//                                channel.sendFile("", false, is, "img.png");
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    } else if (cmd.equals("ga") || cmd.equals("guerillaall")) {
//                        event.getMessage().delete();
//                        outputAllGuerillaImgs(channel);
//                    } else if (cmd.equals("forceupdateguerilla") || cmd.equals("fug") || cmd.equals("fgu")) {
//                        paddata.updateGuerillas();
//                        Sender.sendMessage(channel, "Guerillas have been updated for today.");
//                    } else if (cmd.equals("pic") || cmd.equals("picjp")) {

//                    } else if (cmd.equals("updatedb") || cmd.equals("update")) {

//                    } else if (cmd.equals("addnickname") || cmd.equals("an")) {
//
//                    } else if (cmd.equals("deletenickname") || cmd.equals("dn")) {
//
//                    } else if (cmd.equals("addguerillachannel") || cmd.equals("agc")) {
//                        if (userHasPerm(user, guild, Permissions.MANAGE_SERVER) || userHasPerm(user, guild, Permissions.MANAGE_CHANNEL)
//                                || userHasPerm(user, guild, Permissions.MANAGE_MESSAGES)) {
//                            if (split.length > 1) {
//                                Perms perms = PermissionsListener.getPermissionDB(guild);
//
//                                ArrayList<IChannel> channelsAdded = new ArrayList<>();
//                                for (int i = 1; i < split.length; i++) {
//                                    ArrayList<String> chans = new ArrayList<>(Arrays.asList(perms.getPerms(tableName, col1, guerillasField, col2).split(";")));
//                                    IChannel aChannel = null;
//                                    if (split[i].contains("#")) {
//                                        aChannel = guild.getChannelByID(Long.parseLong(split[i].replace("<#", "").replace(">", "")));
//                                        if (aChannel != null) {
//                                            if (!chans.contains(aChannel.getStringID())) {
//                                                channelsAdded.add(aChannel);
//                                                perms.addPerms(tableName, col1, guerillasField, col2, aChannel.getLongID() + "");
//                                            }
//                                        }
//                                    }
//                                }
//                                String output = "The guerillas will now additionally be posted in: ";
//                                if (channelsAdded.size() > 0) {
//                                    for (IChannel c : channelsAdded) {
//                                        output += c.mention() + " ";
//                                    }
//                                } else
//                                    output = "No channels were added, make sure you mention the channel(s) with `#`";
//                                Sender.sendMessage(channel, output);
//
//                                perms.close();
//                            }
//                        }
//                    } else if (cmd.equals("deleteguerillachannel") || cmd.equals("dgc")) {
//                        if (userHasPerm(user, guild, Permissions.MANAGE_SERVER) || userHasPerm(user, guild, Permissions.MANAGE_CHANNEL)
//                                || userHasPerm(user, guild, Permissions.MANAGE_MESSAGES)) {
//                            if (split.length > 1) {
//                                Perms perms = PermissionsListener.getPermissionDB(guild);
//
//                                ArrayList<IChannel> channelsToKeep = new ArrayList<>();
//                                ArrayList<IChannel> channelsToDel = new ArrayList<>();
//                                for (int i = 1; i < split.length; i++) {
//                                    IChannel aChannel = null;
//                                    if (split[i].contains("#")) {
//                                        aChannel = guild.getChannelByID(Long.parseLong(split[i].replace("<#", "").replace(">", "")));
//                                        if (aChannel != null) {
//                                            channelsToDel.add(aChannel);
//                                        }
//                                    }
//                                }
//
//                                ArrayList<String> channels = new ArrayList<>(Arrays.asList(perms.getPerms(tableName, col1, guerillasField, col2).split(";")));
//                                perms.setPerms(tableName, col1, guerillasField, col2, "");
//                                for (String s : channels) {
//                                    IChannel ch = null;
//                                    ch = guild.getChannelByID(Long.parseLong(s));
//                                    if (ch != null) {
//                                        if (!channelsToDel.contains(ch)) {
//                                            perms.addPerms(tableName, col1, guerillasField, col2, s);
//                                        }
//                                    }
//                                }
//
//                                String output = "The following guerilla channels were removed: ";
//                                if (channelsToDel.size() > 0) {
//                                    for (IChannel c : channelsToDel) {
//                                        output += c.mention() + " ";
//                                    }
//                                } else
//                                    output = "No channels were removed, make sure you mention the channel(s) with `#`";
//
//                                Sender.sendMessage(channel, output);
//
//                                perms.close();
//                            }
//                        }
//                    } else if (cmd.equals("guerillachannels") || cmd.equals("gc")) {
//                        if (userHasPerm(user, guild, Permissions.MANAGE_SERVER) || userHasPerm(user, guild, Permissions.MANAGE_CHANNEL)
//                                || userHasPerm(user, guild, Permissions.MANAGE_MESSAGES)) {
//                            Perms perms = PermissionsListener.getPermissionDB(guild);
//                            String[] channels = perms.getPerms(tableName, col1, guerillasField, col2).split(";");
//                            String output = "Guerilla channels are: ";
//                            for (int i = 0; i < channels.length; i++) {
//                                IChannel theChan = guild.getChannelByID(Long.parseLong(channels[i]));
//                                if (theChan != null) {
//                                    output += theChan.mention() + " ";
//                                }
//                            }
//
//                            Sender.sendMessage(channel, output);
//                            perms.close();
//                        }
//                    } else if (cmd.equals("buttoncalc") || cmd.equals("bc")) {
//                        double attack = 0.0;
//                        double plusses = 0.0;
//                        int atkL = 0;
//                        int atkPL = 0;
//                        boolean coop = false;
//                        double inheritatk = 0.0;
//                        double nuke = 1.0;
//
//                        Matcher m1 = p1.matcher(msg);
//                        if (m1.matches()) {
//                            Matcher m2 = p2.matcher(msg);
//                            Matcher m3 = p3.matcher(msg);
//
//                            if (m2.find()) attack = Double.parseDouble(m2.group());
//                            if (m2.find()) plusses = Double.parseDouble(m2.group());
//                            if (m2.find()) atkL = Integer.parseInt(m2.group());
//                            if (m2.find()) atkPL = Integer.parseInt(m2.group());
//                            if (m2.find()) inheritatk = Double.parseDouble(m2.group());
//                            if (m2.find()) nuke = Double.parseDouble(m2.group());
//                            if (m3.find())
//                                switch (m3.group().trim().charAt(0)) {
//                                    case 'T':
//                                    case 't':
//                                    case 'Y':
//                                    case 'y':
//                                        coop = true;
//                                        break;
//                                }
//
//                            double finalatk = ((attack + (plusses * 5) + ((attack * 0.01) * atkL) + ((attack * 0.03) * atkPL)) + Math.floor(inheritatk * 0.05)) * (coop ? 1.5 : 1.0);
//                            DecimalFormat df = new DecimalFormat("##,##,##,##,##,##,##0.00");
//                            if (nuke == 1)
//                                Sender.sendMessage(channel, "Attack Base = " + df.format(finalatk));
//                            else
//                                Sender.sendMessage(channel, "Nuke Damage = " + df.format(finalatk * nuke));
//                        } else
//                            Sender.sendMessage(channel, "&buttoncalc <base atk> <atk plusses> <atk lnts> <atk+ lnts> <coop: Y/N> <inherit base atk> <nuke amt>");
//                    }
//                }
//            }
//        }
//
//    }


//
//
//    @EventSubscriber
//    public void startPADThread(GuildCreateEvent event) {
//        if (false) // TODO temp while guerilla_data.json is not being updated.
//            if (!threadRunning) {
//                LocalTime targetTime = LocalTime.of(7, 0);
//                Thread t = new Thread("guerilla") {
//                    @Override
//                    public void run() {
//                        threadRunning = true;
//                        while (true) {
//                            LocalTime current = LocalTime.now();
//                            if (current.equals(targetTime) || current.isAfter(targetTime)) {
//                                List<IGuild> allGuilds = PADModule.client.getGuilds();
//                                for (IGuild guild : allGuilds) {
//                                    Perms perms = PermissionsListener.getPermissionDB(guild);
//                                    ArrayList<String> channels = new ArrayList<>(Arrays.asList(perms.getPerms(tableName, col1, guerillasField, col2).split(";")));
//                                    for (String s : channels) {
//                                        if (s.length() == 0)
//                                            break;
//                                        IChannel channel;
//                                        channel = guild.getChannelByID(Long.parseLong(s));
//                                        if (channel != null && PermissionsListener.isModuleOn(guild, PADModule.name)
//                                                && PermissionsListener.canModuleInChannel(guild, PADModule.name, channel)) {
//                                            LocalDateTime today = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
//                                            IMessage lastMessage = null;
//                                            for (IMessage m : channel.getMessageHistory(50)) {
//                                                if (m.getAuthor().getLongID() == PADModule.client.getOurUser().getLongID()) {
//                                                    lastMessage = m;
//                                                    break;
//                                                }
//                                            }
//                                            if (lastMessage != null) {
//                                                LocalDateTime mDate = LocalDateTime.ofInstant(lastMessage.getTimestamp(), ZoneId.systemDefault());
//                                                if (!(today.getYear() == mDate.getYear() && today.getMonth() == mDate.getMonth() && today.getDayOfMonth() == mDate.getDayOfMonth()))
//                                                    outputAllGuerillaImgs(channel);
//                                            } else
//                                                outputAllGuerillaImgs(channel);
//                                            try {
//                                                sleep(1000 * 60 * 30);//1000 millis = 1s; "Roughly" 30min sleep
//                                            } catch (InterruptedException e) {
//                                                e.printStackTrace();
//                                            }
//                                        }
//                                    }
//                                    perms.close();
//                                }
//                            }
//                        }
//                    }
//                };
//                t.start();
//            }
//    }
//
//    private void addMonsterEmbedReactions(IMessage message, String region) {
//        RequestBuffer.request(() -> {
//            message.addReaction(EmojiManager.getForAlias("arrow_left"));
//
//            RequestBuffer.request(() -> {
//                message.addReaction(EmojiManager.getForAlias("arrow_right"));
//
//                RequestBuffer.request(() -> {
//                    if (region.equals("JP"))
//                        message.addReaction(EmojiManager.getForAlias("regional_indicator_symbol_n"));
//                    else message.addReaction(EmojiManager.getForAlias("regional_indicator_symbol_j"));
//
//                    RequestBuffer.request(() -> message.addReaction(EmojiManager.getForAlias("x")));
//                });
//            });
//        });
//    }
//
//    @EventSubscriber
//    public void onReaction(ReactionAddEvent event) {
//        if (!(event.getMessage().getChannel() instanceof IPrivateChannel)) {
//            IMessage message = event.getMessage();
//            if (message.getAuthor().equals(Eschamali.client.getOurUser())) {
//                List<IReaction> reactions = message.getReactions();
//                if (reactions.size() >= 4) {
//                    // For Monster Embeds:
//                    // 0 = Left Arrow : ⬅
//                    // 1 = Right Arrow : ➡
//                    // 2 = NA/JP : 🇳 / 🇯
//                    // 3 = X : ❌
//                    final long REQUESTLIMITDELAY = 2000;
//                    int usersReacted = event.getReaction().getUsers().size();
//                    List<IUser> users = event.getReaction().getUsers();
//                    boolean containsBot = users.contains(Eschamali.client.getOurUser());
//                    if (containsBot && usersReacted >= 2) {
//                        String reactionName = event.getReaction().getEmoji().getName();
//                        if (reactionName.equals("❌")) { // Delete Embed
//                            RequestBuffer.request(() -> message.delete());
//                        } else {
//                            IEmbed embed = event.getMessage().getEmbeds().get(0);
//                            int number = Integer.parseInt(embed.getTitle().split("\\.")[1].split(" ")[0]);
//                            List<IEmbed.IEmbedField> fields = embed.getEmbedFields();
//                            String evos = "";
//                            for (int i = 0; i < fields.size(); i++) {
//                                if (fields.get(i).getName().equals("Other Evos"))
//                                    evos = fields.get(i).getValue();
//                            }
//
//                            String[] evosArray = {};
//                            int prevIndex = -1;
//                            if (evos.length() > 0) {
//                                evosArray = evos.split(", ");
//                                for (int i = 0; i < evosArray.length; i++) {
//                                    int current = Integer.parseInt(evosArray[i]);
//                                    if (number > current) prevIndex = i;
//                                }
//                            }
//
//                            switch (reactionName) {
//                                case "⬅": // Decrement Evo
//                                    if (prevIndex != -1) {
//                                        String region = "NA";
//                                        for (int i = 0; i < reactions.size(); i++) {
//                                            if (reactions.get(i).getEmoji().getName().equals("\uD83C\uDDF3"))
//                                                region = "JP";
//                                        }
//                                        Monster m_dec = paddata.getMonster(evosArray[prevIndex], region);
//                                        final String fRegion = region;
//                                        if (m_dec != null)
//                                            RequestBuffer.request(() -> message.edit(getInfoEmbed(m_dec, m_dec.getCard_id() + "", fRegion)));
//                                    }
//                                    reactions.forEach(r -> {
//                                        String name = r.getEmoji().getName();
//                                        if (name.equals("⬅")) {
//                                            List<IUser> rUsers = r.getUsers();
//                                            rUsers.forEach(u -> {
//                                                if (!u.equals(Eschamali.client.getOurUser()))
//                                                    RequestBuffer.request(() -> message.removeReaction(u, r));
//                                                try {
//                                                    Thread.sleep(REQUESTLIMITDELAY);
//                                                } catch (InterruptedException e) {
//                                                    e.printStackTrace();
//                                                }
//                                            });
//                                        }
//                                    });
//                                    break;
//                                case "➡": // Increment Evo
//                                    if (prevIndex + 1 < evosArray.length) {
//                                        String region = "NA";
//                                        for (int i = 0; i < reactions.size(); i++) {
//                                            if (reactions.get(i).getEmoji().getName().equals("\uD83C\uDDF3"))
//                                                region = "JP";
//                                        }
//                                        Monster m_dec = paddata.getMonster(evosArray[prevIndex + 1], region);
//                                        final String fRegion = region;
//                                        if (m_dec != null)
//                                            RequestBuffer.request(() -> message.edit(getInfoEmbed(m_dec, m_dec.getCard_id() + "", fRegion)));
//                                    }
//                                    reactions.forEach(r -> {
//                                        String name = r.getEmoji().getName();
//                                        if (name.equals("➡")) {
//                                            List<IUser> rUsers = r.getUsers();
//                                            rUsers.forEach(u -> {
//                                                if (!u.equals(Eschamali.client.getOurUser()))
//                                                    RequestBuffer.request(() -> message.removeReaction(u, r));
//                                                try {
//                                                    Thread.sleep(REQUESTLIMITDELAY);
//                                                } catch (InterruptedException e) {
//                                                    e.printStackTrace();
//                                                }
//                                            });
//                                        }
//                                    });
//                                    break;
//                                case "\uD83C\uDDF3": // Switch to NA
//                                    Monster m_na = paddata.getMonster(number + "", "NA");
//                                    RequestBuffer.request(() -> message.edit(getInfoEmbed(m_na, number + "", "NA")));
//                                    RequestBuffer.request(() -> {
//                                        try {
//                                            Thread.sleep(REQUESTLIMITDELAY);
//                                        } catch (InterruptedException e) {
//                                            e.printStackTrace();
//                                        }
//                                        message.removeAllReactions();
//                                        try {
//                                            Thread.sleep(REQUESTLIMITDELAY);
//                                        } catch (InterruptedException e) {
//                                            e.printStackTrace();
//                                        }
//                                        RequestBuffer.request(() -> addMonsterEmbedReactions(message, "NA"));
//                                    });
//                                    break;
//                                case "\uD83C\uDDEF": // Switch to JP
//                                    Monster m_jp = paddata.getMonster(number + "", "JP");
//                                    RequestBuffer.request(() -> message.edit(getInfoEmbed(m_jp, number + "", "JP")));
//                                    RequestBuffer.request(() -> {
//                                        try {
//                                            Thread.sleep(REQUESTLIMITDELAY);
//                                        } catch (InterruptedException e) {
//                                            e.printStackTrace();
//                                        }
//                                        message.removeAllReactions();
//                                        try {
//                                            Thread.sleep(REQUESTLIMITDELAY);
//                                        } catch (InterruptedException e) {
//                                            e.printStackTrace();
//                                        }
//                                        RequestBuffer.request(() -> addMonsterEmbedReactions(message, "JP"));
//                                    });
//                                    break;
//                            }
//                        }
//                    }
//                }
//            }
//
//        }
//    }
//


//    public void outputAllGuerillaImgs(IChannel channel) {
//        Guerilla g = paddata.getTodayGuerilla();
//
//        BufferedImage pstImg = paddata.guerillaToImage(g, "pst");
//        BufferedImage mstImg = paddata.guerillaToImage(g, "mst");
//        BufferedImage cstImg = paddata.guerillaToImage(g, "cst");
//        BufferedImage estImg = paddata.guerillaToImage(g, "est");
//        ArrayList<BufferedImage> images = new ArrayList<>();
//        images.add(pstImg);
//        images.add(mstImg);
//        images.add(cstImg);
//        images.add(estImg);
//
//        for (BufferedImage bi : images) {
//            try {
//                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                ImageIO.write(bi, "png", os);
//                InputStream is = new ByteArrayInputStream(os.toByteArray());
//                channel.sendFile("", false, is, "img.png");
//            } catch (RateLimitException e) {
//                try {
//                    long delay = e.getRetryDelay();
//                    Thread.sleep(delay);
//                    ByteArrayOutputStream os = new ByteArrayOutputStream();
//                    ImageIO.write(bi, "png", os);
//                    InputStream is = new ByteArrayInputStream(os.toByteArray());
//                    channel.sendFile("", false, is, "img.png");
//                } catch (InterruptedException | IOException e1) {
//                    e1.printStackTrace();
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    public String searchDungeon(String keyword) {
//        if (abbrDun.containsKey(keyword)) {
//            keyword = abbrDun.get(keyword);
//        }
//        try {
//            keyword = keyword.replace(" ", "+").trim();
//            URL url = new URL("http://puzzledragonx.com/en/search.asp?q=" + keyword);
//            Document doc = Jsoup.parse(url, 15000);
//            if (url.toString().equals(doc.location())) {
//                Elements search = doc.select("div#searchresult2").select("tbody").select("tr");
//                String linkID = search.get(0).getElementsByClass("sname").select("a[href]").attr("href");
//                Document dunDoc = Jsoup.parse(new URL("http://puzzledragonx.com/en/" + linkID), 15000);
//                String dunID = dunDoc.select("td.value-end").select("a[href]").attr("href");
//                return "http://puzzledragonx.com/en/" + dunID;
//            } else {
//                if (doc.location().contains("mission")) {
//                    return doc.location();
//                } else {
//                    return searchDungeon(keyword.replace("+", " ") + " descended");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (IndexOutOfBoundsException e) {
//            return "Keyword did not find a dungeon, try different or more keywords.";
//        }
//        return "Nothing could be found.";
//    }
//

}
