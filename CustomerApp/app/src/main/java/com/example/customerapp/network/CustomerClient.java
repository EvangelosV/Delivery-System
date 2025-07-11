package com.example.customerapp.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import com.example.customerapp.model.ProductItem;
import com.example.customerapp.model.StoreItem;
import com.example.customerapp.model.CartItem;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that embeds the original console‑based Customer code inside an
 * Android‑friendly wrapper: every request runs on a background thread and
 * results are marshalled back to the main thread via callbacks.
 */
public class CustomerClient {

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    private static final String TAG = "CustomerClient";
    private static final String MASTER_HOST = "10.0.2.2"; // Host PC when using Android emulator
    private static final int    MASTER_PORT = 5055;
    private static final double DEFAULT_RADIUS = 5.0; // km

    private static CustomerClient instance;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private CustomerClient() {}

    public static synchronized CustomerClient getInstance() {
        if (instance == null) instance = new CustomerClient();
        return instance;
    }

    // ---------- public API ----------

    public void findStores(double latitude, double longitude,
                           String filterType, String category, int minStars, int maxPrice,
                           Callback<List<StoreItem>> cb) {
        runOnIoThread(() -> {
            try {
                ensureConnection();
                String cmd = String.format("findStores|%f|%f|%f|%s|%s|%d|%d",
                        latitude, longitude, DEFAULT_RADIUS,
                        filterType, category, minStars, maxPrice);
                out.writeObject(cmd);
                out.flush();
                Object resp = in.readObject();
                List<StoreItem> result = parseStores((String) resp);
                postSuccess(cb, result);
            } catch (Exception e) { postError(cb, e); }
        });
    }

    public void getProducts(String storeName, Callback<List<ProductItem>> cb) {
        runOnIoThread(() -> {
            try {
                ensureConnection();
                out.writeObject("getStoreProducts|" + storeName);
                out.flush();
                Object resp = in.readObject();
                List<ProductItem> products = parseProducts((String) resp);
                postSuccess(cb, products);
            } catch (Exception e) { postError(cb, e); }
        });
    }

    public void checkout(List<CartItem> cart, Callback<String> cb) {
        runOnIoThread(() -> {
            try {
                ensureConnection();

                // Group by store
                Map<String,List<CartItem>> byStore = new HashMap<>();
                for (CartItem ci : cart) {
                    byStore
                            .computeIfAbsent(ci.storeName, k -> new ArrayList<>())
                            .add(ci);
                }

                // For each store send a buy|... command
                StringBuilder sb = new StringBuilder();
                // pick first store only? Or loop if you want multi-store in one string,
                // but handleBuy only supports one store per command:
                for (Map.Entry<String,List<CartItem>> e : byStore.entrySet()) {
                    sb.setLength(0);
                    sb.append("buy|").append(e.getKey());
                    for (CartItem ci : e.getValue()) {
                        sb.append("|")
                                .append(ci.productName)
                                .append(",")
                                .append(ci.quantity);
                    }
                    out.writeObject(sb.toString());
                    out.flush();
                    Object resp = in.readObject();
                    // optionally collect or notify on resp
                }

                postSuccess(cb, "All stores purchased");
            } catch (Exception ex) {
                postError(cb, ex);
            }
        });
    }

    // ------- internal helpers -------

    private synchronized void ensureConnection() throws IOException {
        if (socket != null) {
            try {
                // close streams first to be safe
                if (out != null) {
                    out.close();
                    out = null;
                }
                if (in != null) {
                    in.close();
                    in = null;
                }
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing existing connection", e);
            } finally {
                socket = null;
            }
        }
        socket = new Socket(MASTER_HOST, MASTER_PORT);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());
        Log.d(TAG, "Connected to Master at " + MASTER_HOST + ":" + MASTER_PORT);
    }

    private List<StoreItem> parseStores(String payload) {
        List<StoreItem> list = new ArrayList<>();
        if (payload == null || payload.equals("No stores found")) return list;
        String[] stores = payload.split("\\|");
        for (String s : stores) {
            String[] info = s.split(",");
            if (info.length >= 6) {
                String name   = info[0];
                String cat    = info[1];
                int    stars  = Integer.parseInt(info[3]);
                String priceR = info[5]; // $, $$, $$$
                // Use asset path instead of server path
                String logoPath = "file:///android_asset/" + name + "-Logo.png";
                list.add(new StoreItem(name, cat, priceR, stars, logoPath));
            }
        }
        return list;
    }

    private List<ProductItem> parseProducts(String payload) {
        List<ProductItem> list = new ArrayList<>();
        if (payload == null || payload.equals("Store not found") || payload.equals("No products available"))
            return list;
        String[] products = payload.split("\\|");
        for (String p : products) {
            String[] info = p.split(",");
            if (info.length >= 3) {
                String name = info[0];
                double price = Double.parseDouble(info[1]);
                int stock = Integer.parseInt(info[2]);
                list.add(new ProductItem(name, stock, price));
            }
        }
        return list;
    }

    private void runOnIoThread(Runnable r) {
        new Thread(r, "CustomerClient-IO").start();
    }

    private <T> void postSuccess(Callback<T> cb, T data) {
        mainHandler.post(() -> cb.onSuccess(data));
    }

    private void postError(Callback<?> cb, Exception e) {
        mainHandler.post(() -> cb.onError(e));
    }
}