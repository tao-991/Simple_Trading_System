package com.trading.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;

public class PositionStore {
    private final JedisPool pool;

    public PositionStore(String host, int port){
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        this.pool = new JedisPool(config, host, port);
    }

    // Build the Redis key for a given account + symbol
    private String key(String accountId, String symbol){
        return "Position:" + accountId + ":" + symbol;
    }

    // Update position fields after a trade
    public void updatePosition(String accountId, String symbol,
                               long netQty, double avgCostPrice, double realizedPnL){
        try (Jedis jedis = pool.getResource()){
            jedis.hset(key(accountId, symbol), Map.of(
                    "netQty", String.valueOf(netQty),
                    "avgCostPrice", String.valueOf(avgCostPrice),
                    "realizedPnL", String.valueOf(realizedPnL),
                    "lastUpdated", String.valueOf(System.currentTimeMillis())
            ));
        }
    }

    // Get a single field - used by Risk Engine for position limit check
    public long getNetQty(String accountId, String symbol) {
        try (Jedis jedis = pool.getResource()){
            String val = jedis.hget(key(accountId, symbol), "netQty");
            return val == null ? 0L: Long.parseLong(val);

        }
    }

    // Get all fields - used by PnL Engine and Dashboard
    public Map<String, String> getPosition(String accountId, String symbol) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hgetAll(key(accountId, symbol));
        }
    }

    public double getAvgCost(String accountId, String symbol) {
        try (Jedis jedis = pool.getResource()) {
            String val = jedis.hget(key(accountId,symbol), "avgCostPrice");
            return val == null ? 0.0 : Double.parseDouble(val);
        }
    }

    public double getRealizedPnL(String accountId, String symbol) {
        try (Jedis jedis = pool.getResource()) {
            String val = jedis.hget(key(accountId,symbol), "realizedPnL");
            return val == null ? 0.0 : Double.parseDouble(val);
        }
    }

    public void close(){
        pool.close();
    }

}
