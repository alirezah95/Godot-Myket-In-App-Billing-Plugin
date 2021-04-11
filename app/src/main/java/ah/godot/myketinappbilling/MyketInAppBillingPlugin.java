package ah.godot.myketinappbilling;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.godotengine.godot.Dictionary;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ah.godot.myketinappbilling.util.IabHelper;
import ah.godot.myketinappbilling.util.IabResult;
import ah.godot.myketinappbilling.util.Inventory;
import ah.godot.myketinappbilling.util.Purchase;
import ah.godot.myketinappbilling.util.SkuDetails;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class MyketInAppBillingPlugin extends GodotPlugin {
    /*
     * List of signals.
     */
    private final String cConnected = "connected"; // With a boolean indicating success in connection
    private final String cDisconnected = "disconnected";
    private final String cQueryFinished = "query_sku_details_finished"; // With an Array of Dictionary
    private final String cPurchaseFinished = "purchase_finished"; // With a Dictionary
    private final String cTAG = "godot";
    private String mBase64Key = "";
    private Godot mGodot;
    private IabHelper mHelper;
    private Inventory mInventory = null;
    private boolean mHelperSetupFinished = false;
    private boolean mIsStoreInstalled = false;
    /*
     * Adding Listeners.
     */
    private IabHelper.OnIabSetupFinishedListener mSetupFinishedListener =
            new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult result) {
                    mHelperSetupFinished = true;
                    mIsStoreInstalled = result.isSuccess();
                    try {
                        emitSignal(cConnected, mIsStoreInstalled);
                    } catch (Exception e) {
                        Log.d(cTAG, "Emit: " + e.getMessage());
                    }
                }
            };
    private IabHelper.QueryInventoryFinishedListener mQueryInventoryFinished =
            new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    Log.i(cTAG, "Query finished result: " + result.isSuccess());
                    if (result.isSuccess()) {
                        Object[] skuDetails = new Object[inv.getAllProducts().size()];
                        mInventory = inv;
                        byte i = 0;
                        for (Purchase item: mInventory.getAllPurchases()) {
                            Log.d(cTAG, item.getSku() + " " + item.getOrderId());
                        }

                        for (SkuDetails item: mInventory.getAllProducts()) {
                            Log.d(cTAG, item.getSku() + " " + item.getPrice());
                        }

                        for (SkuDetails sku : mInventory.getAllProducts()) {
                            Dictionary currSku = new Dictionary();

                            currSku.set_keys(new String[]{
                                    "product_id", "type", "price", "title", "description"}
                            );
                            currSku.set_values(new String[]{
                                    sku.getSku(), sku.getType(), sku.getPrice(), sku.getTitle(),
                                    sku.getDescription()
                            });
                            skuDetails[i] = currSku;
                            i += 1;
                        }
                        try {
                            emitSignal(cQueryFinished, (Object)skuDetails);
                        } catch (Exception e) {
                            Log.d(cTAG, "Emit: " + e.getMessage());
                        }
                    } else {
                        try {
                            emitSignal(cQueryFinished, new Dictionary().put("status", 1));
                        } catch (Exception e) {
                            Log.d(cTAG, "Emit: " + e.getMessage());
                        }
                    }
                }
            };
    private IabHelper.OnIabPurchaseFinishedListener mPurchaseFinished =
            new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    Dictionary purchResult = new Dictionary();

                    if (result.isSuccess()) {
                        purchResult.put("status", 0); // OK
                    } else {
                        purchResult.put("status", 1);
                        purchResult.put("error", result.getMessage());
                        purchResult.put("code", result.getResponse());
                    }
                    try {
                        emitSignal(cPurchaseFinished, purchResult);
                    } catch (Exception e) {
                        Log.d(cTAG, "Emit: " + e.getMessage());
                    }
                }
            };


    public MyketInAppBillingPlugin(Godot godot) {
        super(godot);
        mGodot = godot;

        return;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "MyketInAppBilling";
    }

    @NonNull
    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList("startConnection", "endConnection", "querySkuDetails",
                "setApplicationKey", "purchase", "queryPurchases");
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new HashSet<>();
        signals.add(new SignalInfo(cConnected, Boolean.class));
        signals.add(new SignalInfo(cDisconnected));
        signals.add(new SignalInfo(cQueryFinished, Object[].class));
        signals.add(new SignalInfo(cPurchaseFinished, Dictionary.class));

        return signals;
    }

    public void setApplicationKey(String key) {
        mBase64Key = key;
        return;
    }

    public int startConnection() {
        Log.i(cTAG, "starting connection.");

        if (mBase64Key == "") {
            Log.d(cTAG, "Set Application key first with \"setApplicationKey\" function");
            return 1;
        }

        mHelper = new IabHelper(mGodot, mBase64Key);
        try {
            mHelper.startSetup(mSetupFinishedListener);
        } catch (Exception e) {
            Log.i(cTAG, "Start Connection: " + e.toString());
            return 1;
        }
        return 0;
    }

    public void endConnection() {
        mHelper.dispose();
        return;
    }

    public void querySkuDetails() {
        try {
            mGodot.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHelper.queryInventoryAsync(mQueryInventoryFinished);
                }
            });
        } catch (Exception e) {
            Log.d(cTAG,"Query: " + e.getMessage());
        }
        return;
    }

    public Dictionary queryPurchases() {
        if (mInventory == null) {
            return null;
        }

        Object[] purchasesArray = new Object[mInventory.getAllPurchases().size()];
        byte i = 0;
        for (Purchase pur: mInventory.getAllPurchases()) {
            Dictionary dictionary = new Dictionary();
            dictionary.put("order_id", pur.getOrderId());
            dictionary.put("package_name", pur.getPackageName());
            dictionary.put("purchase_state", pur.getPurchaseState());
            dictionary.put("purchase_time", pur.getPurchaseTime());
            dictionary.put("signature", pur.getSignature());
            dictionary.put("sku", pur.getSku());

            purchasesArray[i] = dictionary;
            i += 1;
        }

        Dictionary purchases = new Dictionary();
        purchases.put("purchases", purchasesArray);

        return purchases;
    }

    public Dictionary purchase(String productId) {
        Dictionary result = new Dictionary();

//        SkuDetails sku = mInventory.getSkuDetails(productId);
//
//        if (sku == null) {
//            result.put("status", 1);
//            result.put("error", "No product found with product_id: " + productId);
//
//            return result;
//        }

        mHelper.launchPurchaseFlow(mGodot, productId, 1,
                mPurchaseFinished);

        result.put("status", 0);

        return result;
    }
}
