package online.meinkraft.customvillagertrades.listener;

import online.meinkraft.customvillagertrades.CustomVillagerTrades;
import online.meinkraft.customvillagertrades.villager.VillagerManager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

public class EntityRemoveListener implements Listener {

    private final CustomVillagerTrades plugin;

    public EntityRemoveListener(CustomVillagerTrades plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Villager))
            return;

        VillagerManager villagerManager = plugin.getVillagerManager();
        villagerManager.remove((Villager) event.getEntity());

    }
}
