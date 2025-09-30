/**
 * Fast set blocks
 * Because default world.setBlock or block.setType is very slow (lighting + packets)
 */
package phonon.nms.blockedit

import java.util.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import phonon.nodes.nms.NMSBlockPos
import phonon.nodes.nms.NMSBlockState
import phonon.nodes.nms.NMSChunk
import phonon.nodes.nms.NMSPacketLevelChunkWithLightPacket
import phonon.nodes.nms.CraftWorld
import phonon.nodes.nms.CraftPlayer
import phonon.nodes.nms.CraftMagicNumbers

// 1.21.1 特定的 import
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.server.level.ServerLevel

public class FastBlockEditSession(
    val bukkitWorld: org.bukkit.World
) {
    // nms world
    private val world = (bukkitWorld as CraftWorld).getHandle()
    
    // blocks that were modified in this session
    private val modified: HashMap<NMSBlockPos, NMSBlockState> = hashMapOf()

    /**
     * Mark a block as updated to a new material. This is lazy
     * and does not actually change world until `execute` is called.
     */
    公共 fun setBlock(x: Int, y: Int, z: Int, material: Material) {
        modified.put(NMSBlockPos(x, y, z), CraftMagicNumbers.getBlock(material).defaultBlockState())
    }

    /**
     * Get material of block at location (x, y, z) in this session's
     * world. This will also check the modified material stored in this
     * session object. 
     */
    public fun getBlock(x: Int, y: Int, z: Int): Material {
        val bData = modified.get(NMSBlockPos(x, y, z))
        if ( bData != null ) {
            return CraftMagicNumbers.getMaterial(bData).getItemType()
        }
        return Location(bukkitWorld, x.toDouble(), y.toDouble(), z.toDouble()).getBlock().getType()
    }

    /**
     * Run the block changes and send updates to players in view distance.
     * Updated for Minecraft 1.21.1
     */
    公共 fun execute(updateLighting: Boolean) {
        //modify blocks
        val chunks: HashSet<NMSChunk> = hashSetOf()
        for ( (bPos, bState) in modified ) {
            // 1.21.1 正确的获取chunk方式
            val chunkX = bPos.getX() shr 4
            val chunkZ = bPos.getZ() shr 4
            
            // 方法1：使用 ChunkStatus (推荐)
            val chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true)
            
            if ( chunk !== null ) {
                chunks.add(chunk)
                // 1.21.1 中 setBlockState 可能需要不同的参数
                // 第三个参数通常是 updateLight (boolean)
                chunk.setBlockState(bPos, bState, false)
            }
        }

        // update lighting - 1.21.1 中的 Lighting 引擎
        val lightingEngine = world.getLightEngine()
        if ( updateLighting ) {
            for ( pos in modified.keys ) {
                lightingEngine.checkBlock(pos)
            }
        }

        // send chunk updates to players
        for ( chunk in chunks ) {
            // 1.21.1 中的 Packet 创建方式
            val packetChunk = NMSPacketLevelChunkWithLightPacket(chunk, world.getLightEngine(), null, null, true)

            for ( p in Bukkit.getOnlinePlayers() ) {
                val nmsPlayer = (p as CraftPlayer).getHandle()
                val dist = Bukkit.getViewDistance() + 1
                val playerChunk = nmsPlayer.chunkPosition()
                
                if ( chunk.getPos().x < playerChunk.x - dist ||
                     chunk.getPos().x > playerChunk.x + dist ||
                     chunk.getPos().z < playerChunk.z - dist ||
                     chunk.getPos().z > playerChunk.z + dist ) {
                    continue
                }
                nmsPlayer.connection.send(packetChunk)
            }
        }
    }
}
