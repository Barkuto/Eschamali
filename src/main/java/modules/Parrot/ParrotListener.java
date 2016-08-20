package modules.Parrot;

import base.ModuleListener;
import modules.BufferedMessage.BufferedMessage;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

/**
 * Created by Iggie on 8/14/2016.
 */
public class ParrotListener {

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) {
        if (ModuleListener.isModuleOn(event.getMessage().getGuild(), ParrotModule.name)) {
            BufferedMessage.sendMessage(ParrotModule.client, event, event.getMessage().getContent());
        }
    }
}
