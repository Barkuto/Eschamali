import importlib
import discord
from discord import Embed, ActivityType, Game, Colour
from discord.ext import commands
from discord.ext.commands import ExtensionError
from discord.utils import get
import math
import os.path
import sys
import traceback
from datetime import datetime, timezone

import textwrap
import ast
import signal
from io import StringIO
from contextlib import redirect_stdout

UTILS = importlib.import_module('.utils', 'util')
VARS = UTILS.VARS
DB = UTILS.DB
LOGGER = VARS.LOGGER


class TimeOutException(Exception):
    pass


def timeout_handler(signum, frame):
    raise TimeOutException()


class General(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.last_result = None
        self.last_cmd = None

    @commands.command(ignore_extra=False)
    async def ping(self, ctx):
        await ctx.send('pong!')

    @commands.command(aliases=['g'])
    async def google(self, ctx, *query):
        await ctx.send('https://www.google.com/?q=' + '+'.join(query))

    @commands.command()
    async def donate(self, ctx):
        await ctx.send('Donate for server/development funds at: https://streamelements.com/barkuto/tip')

    @commands.command()
    async def maker(self, ctx):
        await ctx.send('Made by **Barkuto**#2315 specifically for Puzzle and Dragons servers. Code at https://github.com/Barkuto/Eschamali')

    @commands.command()
    async def tilt(self, ctx):
        await ctx.send('*T* *I* *L* *T* *E* *D*')

    @commands.command()
    async def riot(self, ctx):
        await ctx.send('ヽ༼ຈل͜ຈ༽ﾉ RIOT ヽ༼ຈل͜ຈ༽ﾉ')

    @commands.command(aliases=['uinfo'])
    async def userinfo(self, ctx, query=None):
        user = ctx.author
        if query:
            if ctx.message.mentions:
                user = ctx.message.mentions[0]
            else:
                user = UTILS.find_member(ctx.guild, query)
                if not user:
                    return await ctx.send('Could not find that user.')
        name = user.name
        disc = user.discriminator
        nick = user.nick
        avatar = user.avatar_url
        created = user.created_at
        joined = user.joined_at
        roles = [r for r in sorted(user.roles, reverse=True) if not r.name == '@everyone']
        status = user.status
        activities = user.activities
        users_by_joined = sorted(ctx.guild.members, key=lambda m: m.joined_at)
        footer = f'Member #{users_by_joined.index(user) + 1} | ID: {user.id}'

        activity_str = f'**{status.name.upper()}**'
        for a in activities:
            if a.type == ActivityType.playing:
                activity_str += '\nPlaying **%s %s**' % (
                    a.name, '' if isinstance(a, Game) else ('- ' + a.details))
            elif a.type == ActivityType.streaming:
                activity_str += f'\nStreaming **{a.name}**'
            elif a.type == ActivityType.listening:
                activity_str += f'\nListening to **{a.name}**'
            elif a.type == ActivityType.watching:
                activity_str += f'\nWatching **{a.name}**'
            elif a.type == ActivityType.custom:
                activity_str += f'\n**{a.name}**'

        fmt = '%b %d, %Y %I:%M %p'
        now = datetime.now().astimezone()
        created = created.replace(tzinfo=timezone.utc).astimezone(tz=now.tzinfo)
        joined = joined.replace(tzinfo=timezone.utc).astimezone(tz=now.tzinfo)
        e = Embed(
            title=f'{name}#{disc}' + (f' AKA {nick}' if nick else ''),
            description=activity_str,
            color=roles[0].colour if roles else 0
        ).add_field(
            name='Account Created',
            value=f'{created.strftime(fmt)}\n{(now - created).days} days ago',
            inline=True
        ).add_field(
            name='Guild Joined',
            value=f'{joined.strftime(fmt)}\n{(now - joined).days} days ago',
            inline=True
        ).set_thumbnail(url=avatar).set_footer(text=footer)
        if roles:
            e.add_field(name='Roles',
                        value=' '.join([r.mention for r in roles]),
                        inline=False)
        await ctx.send(embed=e)

    @commands.command(aliases=['sinfo'])
    async def serverinfo(self, ctx):
        guild = ctx.guild
        name = guild.name
        desc = guild.description
        created = guild.created_at
        region = guild.region
        owner = guild.owner
        members = guild.member_count
        roles = guild.roles

        # maybe deal with dst someday
        fmt = '%b %d, %Y %I:%M %p'
        now = datetime.now().astimezone()
        created = created.replace(tzinfo=timezone.utc).astimezone(tz=now.tzinfo)
        e = Embed(
            title=name,
            description=desc
        ).add_field(
            name='Created',
            value=f'{created.strftime(fmt)}',
            inline=True
        ).add_field(
            name='Server Age',
            value=f'{(now - created).days} days',
            inline=True
        ).add_field(
            name='Region',
            value=region,
            inline=True
        ).add_field(
            name='Owner',
            value=owner.mention,
            inline=True
        ).add_field(
            name='Members',
            value=members,
            inline=True
        ).add_field(
            name='Roles',
            value=len(roles),
            inline=True
        ).set_thumbnail(url=guild.icon_url_as()).set_footer(text=guild.id)
        await ctx.send(embed=e)

    @commands.command()
    @commands.check_any(commands.is_owner(),
                        commands.has_permissions(manage_guild=True))
    async def say(self, ctx, *, msg):
        await ctx.message.delete()
        await ctx.send(msg)

    # Based off https://github.com/Cog-Creators/Red-DiscordBot/blob/V3/develop/redbot/core/dev_commands.py#L152
    @commands.command(aliases=['eval'])
    async def ev(self, ctx, *, body=None):
        # Set environment based on author
        if ctx.author.id in ctx.bot.owner_ids:
            if not body and self.last_cmd:
                return await self.ev(ctx, body=self.last_cmd)
            elif not body and not self.last_cmd:
                return
            self.last_cmd = body
            env = {
                '_': self.last_result,
                'discord': discord,
                'ctx': ctx,
                'bot': ctx.bot,
                'author': ctx.author,
                'channel': ctx.channel,
                'msg': ctx.message,
                'guild': ctx.guild,
                'db': UTILS.get_server_db(ctx),
                'UTILS': UTILS
            }
        else:
            if not body:
                return
            blacklist = ['import', '__', 'eval', 'exec', 'compile', 'getattr']
            run = True
            for s in blacklist:
                run = run and not s in body
            if not run:
                return await ctx.send('Nice try, Joe :smirk:')
            env = {
                '__builtins__': {},
                'print': print,
                'range': range
            }
        # Add math methods to either environment
        for fname in dir(math):
            if not '__' in fname:
                env[fname] = getattr(math, fname)

        # Remove code block from body
        # Add a return statement if code is one-liner
        if body.startswith('```') and body.endswith('```'):
            body = body.replace('```python', '').replace('```py', '')[:-3].replace('```', '')
        body = body.strip(' \n')
        body_split = body.split('\n')
        if len(body_split) == 1:
            body = f'return {body}'

        # Compile function with body, throw syntax error if bad
        stdout = StringIO()
        to_compile = 'async def func():\n%s' % textwrap.indent(body, '  ')
        signal.signal(signal.SIGALRM, timeout_handler)
        try:
            compiled = compile(to_compile, '<string>', 'exec', flags=ast.PyCF_ALLOW_TOP_LEVEL_AWAIT, optimize=0)
            exec(compiled, env)
        except SyntaxError as e:
            return await ctx.send(f'```py\n{e}```')

        func = env['func']
        result = None
        try:
            with redirect_stdout(stdout):
                signal.alarm(10)
                result = await func()
        except TimeOutException as e:
            printed = traceback.format_exc()
        except:
            printed = f'{stdout.getvalue()}{traceback.format_exc()}'
        else:
            printed = stdout.getvalue()
        signal.alarm(0)

        if result is not None:
            if ctx.author.id in ctx.bot.owner_ids:
                self.last_result = result
            msg = f'{printed}{result}'
        else:
            msg = printed
        msg = msg.replace(self.bot.config['token'], '[REDACTED]')
        msg = msg.replace(os.path.join(os.path.dirname(__file__)), '....')
        if msg:
            if len(msg) >= 2000:
                await ctx.send(f'```Output too big.```')
            else:
                await ctx.send(f'```py\n{msg}```')
        else:
            await ctx.send('```No output.```')

    @commands.command(aliases=['tb'])
    @commands.is_owner()
    async def traceback(self, ctx, num: int = 1):
        if self.bot.tbs:
            for i in range(0, min(len(self.bot.tbs), num)):
                split_str = '\nThe above exception was the direct cause of the following exception:\n'
                tb = self.bot.tbs[i]
                to_send = []
                if len(tb) > (2000 - 10):
                    for m in tb.split(split_str):
                        to_send.append(m)
                if to_send:
                    await ctx.send(f'```py\n{to_send[0]}```')
                    for m in to_send[1:]:
                        await ctx.send(f'```py\n{split_str}{m}```')
                else:
                    await ctx.send(f'```py\n{self.bot.tbs[i]}```')
        else:
            await ctx.send(f'No tracebacks.')

    """
    COG COMMANDS
    """

    @commands.command()
    @commands.is_owner()
    async def load(self, ctx, *cogs):
        for cog_name in cogs:
            cog_name = cog_name.lower()
            try:
                ctx.bot.load_extension(f'{VARS.COGS_DIR_NAME}.{cog_name}')
                ctx.bot.pm.load_cog_cmd_prefixes(cog_name)
                await ctx.send(f'`{cog_name}` loaded.')
            except ExtensionError as e:
                await ctx.send(f'Error loading `{cog_name}`')
                raise e

    @commands.command()
    @commands.is_owner()
    async def reload(self, ctx, *cogs):
        for cog_name in cogs:
            cog_name = cog_name.lower()
            if cog_name == 'all':
                return await self._reload_all(ctx)
            else:
                try:
                    nick = cog_name
                    for c in self.bot.all_cogs():
                        if c.startswith(nick):
                            cog_name = c
                    if not cog_name in [k.lower() for k, _ in self.bot.cogs.items()] and cog_name in self.bot.all_cogs():
                        await self.load(ctx, cog_name)
                    elif not cog_name in self.bot.all_cogs():
                        await ctx.send('Invalid cog.')
                    else:
                        ctx.bot.reload_extension(f'{VARS.COGS_DIR_NAME}.{cog_name}')
                        ctx.bot.pm.load_cog_cmd_prefixes(cog_name)
                        await ctx.send(f'`{cog_name}` reloaded.')
                except ExtensionError as e:
                    await ctx.send(f'Error reloading `{cog_name}`')
                    raise e

    @commands.command()
    @commands.is_owner()
    async def unload(self, ctx, *cogs):
        for cog_name in cogs:
            cog_name = cog_name.lower()
            try:
                ctx.bot.unload_extension(f'{VARS.COGS_DIR_NAME}.{cog_name}')
                ctx.bot.pm.load_cog_cmd_prefixes(cog_name)
                await ctx.send(f'`{cog_name}` unloaded.')
            except ExtensionError as e:
                await ctx.send(f'Error unloading `{cog_name}`')
                raise e

    async def _reload_all(self, ctx):
        all_cogs = ctx.bot.all_cogs()
        reloaded = []
        not_reloaded = []
        for cog in all_cogs:
            try:
                ctx.bot.reload_extension(f'{VARS.COGS_DIR_NAME}.{cog}')
                ctx.bot.pm.load_cog_cmd_prefixes(cog)
                reloaded.append(cog)
            except ExtensionError as e:
                not_reloaded.append(cog)
                raise e
        reloaded = [f'`{c}`' for c in reloaded]
        not_reloaded = [f'`{c}`' for c in not_reloaded]
        await ctx.send(f'Reloaded {" ".join(reloaded)}')
        if(not_reloaded):
            await ctx.send(f'Could not reload {" ".join(not_reloaded)}')

    @commands.command()
    async def cogs(self, ctx):
        loaded = [k.lower() for k, _ in ctx.bot.cogs.items()]
        all_cogs = self.bot.all_cogs()
        unloaded = []
        for c in all_cogs:
            if not c in loaded:
                unloaded.append(c)
        loaded = [f'`{c}`' for c in loaded]
        unloaded = [f'`{c}`' for c in unloaded]
        if loaded:
            await ctx.send(embed=Embed(
                title='Loaded Cogs',
                description=' '.join(loaded),
                colour=discord.Colour.green()
            ))
        if unloaded:
            await ctx.send(embed=Embed(
                title='Unloaded Cogs',
                description=' '.join(unloaded),
                colour=discord.Colour.red()))


def setup(bot):
    bot.add_cog(General(bot))
