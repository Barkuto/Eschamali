import importlib
from discord.ext import commands

UTILS = importlib.import_module('.utils', 'util')
PERMS = importlib.import_module('.perms', 'cogs')
DB = UTILS.DB
CHANNELS_TABLE = PERMS.CHANNELS_TABLE
CHANNELS_TABLE_COL1 = PERMS.CHANNELS_TABLE_COL1
CHANNELS_TABLE_COL2 = PERMS.CHANNELS_TABLE_COL2


class JoinLeave(commands.Cog):
    """Show who joins/leaves in set channels\nSet channels with the "Perms" cog"""

    def __init__(self, bot):
        self.bot = bot

    def _msg(self, user, ending):
        return f'{user.mention} : {user.name}#{user.discriminator} {ending}'

    def _get_channels(self, guild):
        db = UTILS.get_server_db(guild)
        channel_ids = db.get_values(CHANNELS_TABLE, CHANNELS_TABLE_COL2[0], (CHANNELS_TABLE_COL1[0], 'joinleave'))
        channels = [guild.get_channel(int(c)) if c != 'all' else None for c in channel_ids]
        channels = [c for c in channels if c]
        return channels

    @commands.Cog.listener()
    async def on_member_join(self, member):
        for c in self._get_channels(member.guild):
            await c.send(self._msg(member, 'has joined.'))

    @commands.Cog.listener()
    async def on_member_remove(self, member):
        for c in self._get_channels(member.guild):
            await c.send(self._msg(member, 'has left.'))

    @commands.Cog.listener()
    async def on_member_ban(self, guild, user):
        for c in self._get_channels(guild):
            await c.send(self._msg(user, 'has been banned.'))


def setup(bot):
    bot.add_cog(JoinLeave(bot))
