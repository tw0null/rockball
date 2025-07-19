package io.github.tw0null.multiblock

import io.github.tw0null.multiblock.plugin.RockballPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Enderman
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.exp
import kotlin.math.round


class RockSnowBall(private val plugin: RockballPlugin): Listener {
    val keyDistance = NamespacedKey(plugin, "rockball_distance")
    private val startShiftTime = mutableMapOf<UUID, Long>()
    private val displayingPlayers = mutableMapOf<UUID, BukkitRunnable>()

    fun launch(player: Player, distance: Double){
        val rockball = player.launchProjectile(Snowball::class.java).apply {
            velocity = player.location.direction.normalize().multiply(distance)
            persistentDataContainer.set(keyDistance, PersistentDataType.DOUBLE, distance)
            isSilent = true
            isInvisible = true
        }

        Bukkit.getOnlinePlayers().forEach { it.hideEntity(plugin, rockball) }

        val display = player.world.spawn(player.eyeLocation, BlockDisplay::class.java).apply {
            block = Bukkit.createBlockData(Material.COBBLESTONE)
            transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(0.35f, 0.35f, 0.35f),
                Quaternionf()
            )
        }

        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (!rockball.isValid || rockball.isDead || ticks > 1000) {
                    display.transformation.scale.set(0.1f)
                    rockball.world.spawnParticle(
                        Particle.BLOCK_CRUMBLE,                      // 파티클 종류
                        rockball.location,                         // 위치
                        20,                                        // 입자 개수
                        0.2, 0.2, 0.2,                             // 퍼짐 정도
                        0.1,                                       // 속도
                        Material.COBBLESTONE.createBlockData()     // 블럭 타입 지정
                    )
                    cancel()
                    return
                }
                display.teleport(rockball.location)
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)

        player.world.playSound(player.location, Sound.ENTITY_EGG_THROW, 1.0f, 1.0f)
    }

    @EventHandler
    fun onPlayerShift(event: PlayerToggleSneakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        if (!event.isSneaking) return
        startShiftTime[uuid] = System.currentTimeMillis()

        object : BukkitRunnable() {
            override fun run() {
                if (!player.isSneaking || !player.isOnline) {
                    cancel()
                    displayingPlayers.remove(uuid)
                    return
                }
                val duration = player.sneakDuration ?: 0L
                val distance = calculateRockBallDistance(duration)
                val rounded = round(distance * 10) / 10
                player.sendActionBar(Component.text(
                    "$rounded BOOST!",
                ).color(NamedTextColor.AQUA))
            }
        }.runTaskTimer(plugin, 0L, 5L)
    }

    val Player.sneakStart get() = startShiftTime[this.uniqueId]

    val Player.sneakDuration get() =
        player?.sneakStart?.let { System.currentTimeMillis() - it }
    fun calculateRockBallDistance(durationMs: Long): Double {
        val x = durationMs / 1000.0
        val max = 3.0
        val min = 0.7
        val factor = 1 - exp(-x * 1.2)
        return min + (max - min) * factor
    }



    @EventHandler
    fun onSnowballHit(event: EntityDamageByEntityEvent){
        val damager =  event.damager
        if (damager is Snowball && damager.persistentDataContainer.has(keyDistance)) {
            val distance = damager.persistentDataContainer.get(keyDistance, PersistentDataType.DOUBLE) ?: 0.0
            event.damage = 2 * distance * 3
        } else if (damager is Player) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSnowballHitEntity(event: ProjectileHitEvent) {
        val projectile = event.entity
        val hitEntity = event.hitEntity

        if (projectile is Snowball && hitEntity is Enderman) {
            if (projectile.persistentDataContainer.has(keyDistance)) {
                val distance = projectile.persistentDataContainer.get(keyDistance, PersistentDataType.DOUBLE) ?: 0.0
                hitEntity.damage(2 * distance * 3, projectile.shooter as? Entity)
            }
        }
    }


    @EventHandler
    fun onRightClick(event: PlayerInteractEvent){
        val player = event.player

        if (event.hand != EquipmentSlot.HAND) return
        if (!player.isSneaking) return
        if (!event.action.name.contains("RIGHT_CLICK")) return

        val item = event.item ?: return
        if (item.type != Material.COBBLESTONE) return

        val duration = player.sneakDuration ?: 0L
        val distance = calculateRockBallDistance(duration)

        startShiftTime[player.uniqueId] = System.currentTimeMillis()



        // Do not launch on block place

        if (event.clickedBlock != null && event.useInteractedBlock() != Event.Result.DENY) return

        item.amount = item.amount - 1

        if (item.amount <= 0) {
            player.inventory.setItemInMainHand(null)
        }

        launch(player, distance)
    }
}

