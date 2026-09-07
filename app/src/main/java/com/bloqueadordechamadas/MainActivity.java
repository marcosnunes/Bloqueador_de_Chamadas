package com.bloqueadordechamadas;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> requestRoleLauncher;
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private WhitelistAdapter adapter;
    private List<WhitelistItem> whitelistItems = new ArrayList<>();
    private TextView txtEmpty;
    private TextView txtStatusDesc;
    private TextView txtSystemStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupInsets();
        initViews();
        setupLogic();
    }

    private void setupInsets() {
        View toolbar = findViewById(R.id.toolbar);
        View root = findViewById(R.id.main);
        RecyclerView rvWhitelist = findViewById(R.id.rvWhitelist);
        View cardAdd = findViewById(R.id.cardAdd);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(0, systemBars.top, 0, 0);
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) cardAdd.getLayoutParams();
            int baseMargin = (int) (24 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = baseMargin + systemBars.bottom;
            cardAdd.setLayoutParams(lp);

            int basePaddingBottom = (int) (100 * getResources().getDisplayMetrics().density);
            rvWhitelist.setPadding(
                    rvWhitelist.getPaddingLeft(),
                    rvWhitelist.getPaddingTop(),
                    rvWhitelist.getPaddingRight(),
                    basePaddingBottom + systemBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initViews() {
        txtEmpty = findViewById(R.id.txtEmpty);
        txtStatusDesc = findViewById(R.id.txtStatusDesc);
        txtSystemStatus = findViewById(R.id.txtSystemStatus);
        
        RecyclerView rvWhitelist = findViewById(R.id.rvWhitelist);
        rvWhitelist.setLayoutManager(new LinearLayoutManager(this));
        
        whitelistItems = loadWhitelistItems();
        adapter = new WhitelistAdapter(this::onItemActionClicked);
        adapter.updateFullList(whitelistItems);
        rvWhitelist.setAdapter(adapter);
        updateEmptyView();

        findViewById(R.id.btnSync).setOnClickListener(v -> forceSyncContacts());
        
        EditText edtSearch = findViewById(R.id.edtSearch);
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performGlobalSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            EditText edt = findViewById(R.id.edtPhoneNumber);
            String number = edt.getText().toString().trim();
            if (!number.isEmpty()) {
                addNewNumberManually(number);
                edt.setText("");
            }
        });

        findViewById(R.id.btnManageDefault).setOnClickListener(v -> 
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        );
    }

    private void setupLogic() {
        SwitchMaterial switchBlocker = findViewById(R.id.switchBlocker);
        SharedPreferences settings = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        boolean isEnabled = settings.getBoolean("isBlockerEnabled", false);
        switchBlocker.setChecked(isEnabled);
        updateStatusUI(isEnabled);

        switchBlocker.setOnCheckedChangeListener((b, isChecked) -> {
            settings.edit().putBoolean("isBlockerEnabled", isChecked).apply();
            updateStatusUI(isChecked);
        });

        requestRoleLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            updateSystemStatusUI();
        });

        requestPermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (Boolean.TRUE.equals(result.get(Manifest.permission.READ_CONTACTS))) {
                importContactsToWhitelistIfNeeded();
            }
        });

        checkAndRequestPermissions();
        checkAndRequestCallScreeningRole();
        updateSystemStatusUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemStatusUI();
    }

    private void addNewNumberManually(String number) {
        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (findItemByNumber(normalized) != null) {
            Toast.makeText(this, R.string.numero_ja_na_lista, Toast.LENGTH_SHORT).show();
            return;
        }

        String contactName = getContactNameFromSystem(normalized);
        if (contactName == null) contactName = getString(R.string.numero_manual);
        
        WhitelistItem newItem = new WhitelistItem(normalized, contactName, true);
        whitelistItems.add(newItem);
        saveAndRefresh();
        Toast.makeText(this, R.string.numero_salvo_excecao, Toast.LENGTH_SHORT).show();
    }

    private void onItemActionClicked(WhitelistItem clickedItem) {
        if (clickedItem.isInWhitelist) {
            // REMOVER da Whitelist mestre
            WhitelistItem original = findItemByNumber(clickedItem.number);
            if (original != null) {
                whitelistItems.remove(original);
            }
            Toast.makeText(this, R.string.removido, Toast.LENGTH_SHORT).show();
        } else {
            // ADICIONAR à Whitelist mestre
            WhitelistItem newItem = new WhitelistItem(clickedItem.number, clickedItem.name, true);
            if (findItemByNumber(clickedItem.number) == null) {
                whitelistItems.add(newItem);
            }
            Toast.makeText(this, R.string.contato_adicionado, Toast.LENGTH_SHORT).show();
        }
        saveAndRefresh();
    }

    private void saveAndRefresh() {
        sortWhitelistItems();
        saveAllToPrefs();
        
        // 1. Sempre atualiza a base do adapter primeiro
        adapter.updateFullList(whitelistItems);
        
        // 2. Se houver busca ativa, re-executa para sincronizar os resultados visuais
        EditText edtSearch = findViewById(R.id.edtSearch);
        String query = edtSearch.getText().toString();
        if (!query.trim().isEmpty()) {
            performGlobalSearch(query);
        }
        updateEmptyView();
    }

    private void performGlobalSearch(String query) {
        String cleanQuery = query.trim().toLowerCase();
        if (cleanQuery.isEmpty()) {
            adapter.updateSearchResults(null);
            return;
        }

        List<WhitelistItem> searchResults = new ArrayList<>();
        Set<String> processedNumbers = new HashSet<>();

        // 1. Prioridade: Quem JÁ está na Whitelist
        for (WhitelistItem item : whitelistItems) {
            if (item.name.toLowerCase().contains(cleanQuery) || item.number.contains(cleanQuery)) {
                searchResults.add(new WhitelistItem(item.number, item.name, true));
                processedNumbers.add(item.number);
            }
        }

        // 2. Busca nos contatos do sistema (Contatos que PODEM ser adicionados)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            // Usamos uma busca por nome ou número nos contatos globais
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " + 
                               ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
            String[] args = new String[]{"%" + cleanQuery + "%", "%" + cleanQuery + "%"};
            
            try (Cursor cursor = getContentResolver().query(uri, 
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, 
                    selection, args, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String num = cursor.getString(0);
                        String name = cursor.getString(1);
                        String normalized = PhoneNumberUtils.normalizeNumber(num);
                        
                        if (normalized != null && !processedNumbers.contains(normalized)) {
                            searchResults.add(new WhitelistItem(normalized, name, false));
                            processedNumbers.add(normalized);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        
        searchResults.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        adapter.updateSearchResults(searchResults);
    }

    private void forceSyncContacts() {
        Toast.makeText(this, "Atualizando nomes dos contatos...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Map<String, String> contactsMap = getAllSystemContacts();
            runOnUiThread(() -> {
                boolean modified = false;
                for (WhitelistItem item : whitelistItems) {
                    String systemName = contactsMap.get(item.number);
                    if (systemName != null && !systemName.equals(item.name)) {
                        item.name = systemName;
                        modified = true;
                    }
                }
                if (modified) {
                    saveAndRefresh();
                    Toast.makeText(this, "Nomes atualizados com sucesso!", Toast.LENGTH_SHORT).show();
                } else {
                    // Mesmo que não mude nomes, forçamos um refresh visual para garantir sincronia
                    saveAndRefresh();
                    Toast.makeText(this, "A lista já está sincronizada.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Map<String, String> getAllSystemContacts() {
        Map<String, String> map = new HashMap<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return map;
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String num = c.getString(0);
                    if (num != null) map.put(PhoneNumberUtils.normalizeNumber(num), c.getString(1));
                }
            }
        }
        return map;
    }

    private String getContactNameFromSystem(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        try (Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    private void checkAndRequestPermissions() {
        String[] p = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS, Manifest.permission.ANSWER_PHONE_CALLS};
        List<String> list = new ArrayList<>();
        for (String s : p) if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) list.add(s);
        if (!list.isEmpty()) requestPermissionsLauncher.launch(list.toArray(new String[0]));
    }

    private void importContactsToWhitelistIfNeeded() {
        SharedPreferences s = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        if (!s.getBoolean("isContactsImported_v6", false)) {
            new Thread(() -> {
                Map<String, String> contactsMap = getAllSystemContacts();
                runOnUiThread(() -> {
                    for (Map.Entry<String, String> entry : contactsMap.entrySet()) {
                        if (findItemByNumber(entry.getKey()) == null) {
                            whitelistItems.add(new WhitelistItem(entry.getKey(), entry.getValue(), true));
                        }
                    }
                    saveAndRefresh();
                    s.edit().putBoolean("isContactsImported_v6", true).apply();
                });
            }).start();
        }
    }

    private WhitelistItem findItemByNumber(String number) {
        for (WhitelistItem item : whitelistItems) if (Objects.equals(item.number, number)) return item;
        return null;
    }

    private void updateSystemStatusUI() {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        boolean held = rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        txtSystemStatus.setText(held ? R.string.status_vinculado : R.string.status_nao_vinculado);
        txtSystemStatus.setTextColor(ContextCompat.getColor(this, held ? R.color.success_green : R.color.block_red));
    }

    private void updateStatusUI(boolean isEnabled) {
        txtStatusDesc.setText(isEnabled ? R.string.status_ativado : R.string.status_desativado);
        txtStatusDesc.setTextColor(ContextCompat.getColor(this, isEnabled ? R.color.success_green : R.color.block_red));
    }

    private void checkAndRequestCallScreeningRole() {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            requestRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING));
        }
    }

    private List<WhitelistItem> loadWhitelistItems() {
        SharedPreferences n = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        SharedPreferences names = getSharedPreferences("ContactNames", Context.MODE_PRIVATE);
        Set<String> set = n.getStringSet("whitelist", new HashSet<>());
        List<WhitelistItem> list = new ArrayList<>();
        for (String s : set) list.add(new WhitelistItem(s, names.getString(s, "Contato"), true));
        list.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return list;
    }

    private void saveAllToPrefs() {
        SharedPreferences n = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        SharedPreferences names = getSharedPreferences("ContactNames", Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>();
        SharedPreferences.Editor ed = names.edit();
        ed.clear();
        for (WhitelistItem i : whitelistItems) {
            set.add(i.number);
            ed.putString(i.number, i.name);
        }
        n.edit().putStringSet("whitelist", set).apply();
        ed.apply();
    }

    private void sortWhitelistItems() {
        whitelistItems.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
    }

    private void updateEmptyView() {
        txtEmpty.setVisibility(whitelistItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class WhitelistItem {
        String number;
        String name;
        boolean isInWhitelist;

        WhitelistItem(String number, String name, boolean isInWhitelist) {
            this.number = number;
            this.name = name;
            this.isInWhitelist = isInWhitelist;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WhitelistItem that = (WhitelistItem) o;
            return isInWhitelist == that.isInWhitelist && 
                   Objects.equals(number, that.number) && 
                   Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, name, isInWhitelist);
        }
    }

    private static class WhitelistDiffCallback extends DiffUtil.Callback {
        private final List<WhitelistItem> oldList;
        private final List<WhitelistItem> newList;

        WhitelistDiffCallback(List<WhitelistItem> oldList, List<WhitelistItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).number.equals(newList.get(newPos).number);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).equals(newList.get(newPos));
        }
    }

    private static class WhitelistAdapter extends RecyclerView.Adapter<WhitelistAdapter.ViewHolder> {
        private final List<WhitelistItem> items = new ArrayList<>();
        private final List<WhitelistItem> fullWhitelist = new ArrayList<>();
        private final OnActionClickListener listener;
        private List<WhitelistItem> lastSearchResults = null;

        interface OnActionClickListener { void onAction(WhitelistItem item); }

        WhitelistAdapter(OnActionClickListener listener) { this.listener = listener; }

        void updateFullList(List<WhitelistItem> newList) {
            this.fullWhitelist.clear();
            for (WhitelistItem i : newList) {
                this.fullWhitelist.add(new WhitelistItem(i.number, i.name, i.isInWhitelist));
            }
            if (lastSearchResults == null) {
                applyUpdates(this.fullWhitelist);
            }
        }

        void updateSearchResults(List<WhitelistItem> results) {
            this.lastSearchResults = results;
            applyUpdates(results != null ? results : fullWhitelist);
        }

        private void applyUpdates(List<WhitelistItem> newItems) {
            List<WhitelistItem> newList = new ArrayList<>();
            for (WhitelistItem i : newItems) {
                newList.add(new WhitelistItem(i.number, i.name, i.isInWhitelist));
            }
            
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new WhitelistDiffCallback(this.items, newList));
            this.items.clear();
            this.items.addAll(newList);
            result.dispatchUpdatesTo(this);
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_whitelist, p, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            WhitelistItem i = items.get(p);
            h.name.setText(i.name);
            h.number.setText(i.number);
            h.btn.setIconResource(i.isInWhitelist ? android.R.drawable.ic_menu_delete : android.R.drawable.ic_input_add);
            h.btn.setIconTintResource(i.isInWhitelist ? R.color.block_red : R.color.success_green);
            h.btn.setOnClickListener(v -> listener.onAction(i));
        }

        @Override public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, number;
            com.google.android.material.button.MaterialButton btn;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.txtContactName);
                number = v.findViewById(R.id.txtPhoneNumber);
                btn = v.findViewById(R.id.btnAction);
            }
        }
    }
}
