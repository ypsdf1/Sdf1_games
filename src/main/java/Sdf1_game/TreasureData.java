package Sdf1_game;

import org.bukkit.Location;
import java.util.List;

public class TreasureData {
    public TreasureConfig config;
    public Location chestLoc;
    public int bondAmount;
    public List<TreasureReward> rolled;
    public boolean isRefreshed;

    public TreasureData(
            TreasureConfig config,
            Location chestLoc,
            int bondAmount,
            List<TreasureReward> rolled,
            boolean isRefreshed) {
        this.config = config;
        this.chestLoc = chestLoc;
        this.bondAmount = bondAmount;
        this.rolled = rolled;
        this.isRefreshed = isRefreshed;
    }
}
