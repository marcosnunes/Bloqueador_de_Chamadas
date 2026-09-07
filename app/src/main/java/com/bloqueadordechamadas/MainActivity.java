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
import android.widget.Button;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
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
    private List<WhitelistItem> whitelistItems;
    private TextView txtEmpty;
    private TextView txtStatusDesc;
    private TextView txtSystemStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ativa o modo Edge-to-Edge para um visual moderno (Samsung/Google)
        EdgeToEdge.enable(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View toolbar = findViewById(R.id.toolbar);
        View root = findViewById(R.id.main);
        RecyclerView rvWhitelist = findViewById(R.id.rvWhitelist);

        // Aplica insets cirurgicamente para uma experiência Edge-to-Edge real
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Padding no topo para a Toolbar não ficar sob a Status Bar
            toolbar.setPadding(0, systemBars.top, 0, 0);
            
            // Padding nas laterais do root para evitar recortes em telas curvas/landscape
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            
            // A lista (RecyclerView) recebe o padding inferior do sistema
            // Somado ao padding original de 16dp definido no XML
            int basePaddingBottom = (int) (16 * getResources().getDisplayMetrics().density);
            rvWhitelist.setPadding(
                    rvWhitelist.getPaddingLeft(),
                    rvWhitelist.getPaddingTop(),
                    rvWhitelist.getPaddingRight(),
                    basePaddingBottom + systemBars.bottom
            );
            
            return WindowInsetsCompat.CONSUMED;
        });

        txtEmpty = findViewById(R.id.txtEmpty);
        txtStatusDesc = findViewById(R.id.txtStatusDesc);
        txtSystemStatus = findViewById(R.id.txtSystemStatus);
        SwitchMaterial switchBlocker = findViewById(R.id.switchBlocker);
        
        rvWhitelist.setLayoutManager(new LinearLayoutManager(this));
        
        whitelistItems = loadWhitelistItems();
        adapter = new WhitelistAdapter(whitelistItems, this::removeNumberFromWhitelist);
        rvWhitelist.setAdapter(adapter);
        updateEmptyView();

        // Configura o Switch de ativação
        SharedPreferences settings = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        boolean isEnabled = settings.getBoolean("isBlockerEnabled", false);
        switchBlocker.setChecked(isEnabled);
        updateStatusUI(isEnabled);

        switchBlocker.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean("isBlockerEnabled", isChecked).apply();
            updateStatusUI(isChecked);
        });

        findViewById(R.id.btnManageDefault).setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            startActivity(intent);
        });

        // Inicializa o launcher para a requisição da role do sistema
        requestRoleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    updateSystemStatusUI();
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, R.string.definido_padrao, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Inicializa o launcher para permissões
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    boolean contactsGranted = false;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        Boolean granted = entry.getValue();
                        if (granted == null || !granted) {
                            allGranted = false;
                        }
                        if (Manifest.permission.READ_CONTACTS.equals(entry.getKey()) && Objects.equals(granted, Boolean.TRUE)) {
                            contactsGranted = true;
                        }
                    }
                    if (contactsGranted) {
                        importContactsToWhitelistIfNeeded();
                    }
                    if (!allGranted) {
                        Toast.makeText(this, R.string.permissao_necessaria, Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Solicita permissões necessárias
        checkAndRequestPermissions();

        // Verifica e solicita a Role de Call Screening
        checkAndRequestCallScreeningRole();

        // Importa contatos se a permissão já estiver concedida
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            importContactsToWhitelistIfNeeded();
        }

        EditText edtPhoneNumber = findViewById(R.id.edtPhoneNumber);
        Button btnSave = findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> {
            String number = edtPhoneNumber.getText().toString().trim();
            if (!number.isEmpty()) {
                String normalized = PhoneNumberUtils.normalizeNumber(number);
                if (isNumberNotInWhitelist(normalized)) {
                    String contactName = getContactNameFromSystem(normalized);
                    if (contactName == null) {
                        contactName = getString(R.string.numero_manual);
                    }
                    saveNumberToWhitelist(normalized, contactName);
                    Toast.makeText(this, R.string.numero_salvo_excecao, Toast.LENGTH_SHORT).show();
                    edtPhoneNumber.setText("");
                } else {
                    Toast.makeText(this, R.string.numero_ja_na_lista, Toast.LENGTH_SHORT).show();
                }
            }
        });

        EditText edtSearch = findViewById(R.id.edtSearch);
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemStatusUI();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS
        };

        List<String> toRequest = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private String getContactNameFromSystem(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        String name = null;
        // 1. Tenta PhoneLookup (busca flexível/compatível com formatação)
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            try (Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Erro PhoneLookup: " + e.getMessage());
        }

        // 2. Fallback: Se não encontrou, itera pelos números normalizados
        if (name == null) {
            try (Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null) {
                    String cleanTarget = PhoneNumberUtils.normalizeNumber(phoneNumber);
                    while (cursor.moveToNext()) {
                        int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        if (numIdx != -1 && nameIdx != -1) {
                            String num = cursor.getString(numIdx);
                            if (num != null) {
                                String cleanNum = PhoneNumberUtils.normalizeNumber(num);
                                if (cleanTarget.equals(cleanNum) || 
                                    (cleanTarget.length() >= 8 && cleanNum.endsWith(cleanTarget)) ||
                                    (cleanNum.length() >= 8 && cleanTarget.endsWith(cleanNum))) {
                                    name = cursor.getString(nameIdx);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Erro busca contatos fallback: " + e.getMessage());
            }
        }

        return name;
    }

    private void sortWhitelistItems() {
        whitelistItems.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
    }

    private void importContactsToWhitelistIfNeeded() {
        SharedPreferences settings = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        if (!settings.getBoolean("isContactsImported_v4", false)) {
            new Thread(() -> {
                Map<String, String> contactsMap = new HashMap<>();
                try (Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                        null, null, null)) {

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            int numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                            if (numIndex != -1 && nameIndex != -1) {
                                String number = cursor.getString(numIndex);
                                String name = cursor.getString(nameIndex);
                                if (number != null && !number.trim().isEmpty()) {
                                    String normalized = PhoneNumberUtils.normalizeNumber(number);
                                    contactsMap.put(normalized, name);
                                }
                            }
                        }
                    }
                }

                if (!contactsMap.isEmpty()) {
                    runOnUiThread(() -> {
                        boolean modified = false;
                        for (Map.Entry<String, String> entry : contactsMap.entrySet()) {
                            String normalizedNum = entry.getKey();
                            String contactName = entry.getValue();
                            
                            int existingIndex = -1;
                            for (int i = 0; i < whitelistItems.size(); i++) {
                                if (Objects.equals(whitelistItems.get(i).number, normalizedNum)) {
                                    existingIndex = i;
                                    break;
                                }
                            }

                            if (existingIndex == -1) {
                                whitelistItems.add(new WhitelistItem(normalizedNum, contactName));
                                modified = true;
                            } else {
                                WhitelistItem item = whitelistItems.get(existingIndex);
                                if (Objects.equals(item.name, "Contato") || Objects.equals(item.name, getString(R.string.numero_manual))) {
                                    item.name = contactName;
                                    modified = true;
                                }
                            }
                        }
                        if (modified) {
                            sortWhitelistItems();
                            saveAllToPrefs();
                            adapter.updateFullList(whitelistItems);
                            updateEmptyView();
                            Toast.makeText(this, R.string.contatos_importados, Toast.LENGTH_SHORT).show();
                        }
                        settings.edit().putBoolean("isContactsImported_v4", true).apply();
                    });
                } else {
                    settings.edit().putBoolean("isContactsImported_v4", true).apply();
                }
            }).start();
        }
    }

    private WhitelistItem findItemByNumber(String number) {
        for (WhitelistItem item : whitelistItems) {
            if (Objects.equals(item.number, number)) return item;
        }
        return null;
    }

    private boolean isNumberNotInWhitelist(String number) {
        return findItemByNumber(number) == null;
    }

    private void updateSystemStatusUI() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            txtSystemStatus.setText(R.string.status_vinculado);
            txtSystemStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));
        } else {
            txtSystemStatus.setText(R.string.status_nao_vinculado);
            txtSystemStatus.setTextColor(ContextCompat.getColor(this, R.color.block_red));
        }
    }

    private void updateStatusUI(boolean isEnabled) {
        if (isEnabled) {
            txtStatusDesc.setText(R.string.status_ativado);
            txtStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.success_green));
        } else {
            txtStatusDesc.setText(R.string.status_desativado);
            txtStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.block_red));
        }
    }

    private void checkAndRequestCallScreeningRole() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                requestRoleLauncher.launch(intent);
            }
        }
    }

    private List<WhitelistItem> loadWhitelistItems() {
        SharedPreferences numbersPrefs = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        SharedPreferences namesPrefs = getSharedPreferences("ContactNames", Context.MODE_PRIVATE);
        Set<String> numbers = numbersPrefs.getStringSet("whitelist", new HashSet<>());
        List<WhitelistItem> items = new ArrayList<>();
        for (String num : numbers) {
            String name = namesPrefs.getString(num, "Contato");
            items.add(new WhitelistItem(num, name));
        }
        items.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return items;
    }

    private void saveNumberToWhitelist(String number, String name) {
        whitelistItems.add(new WhitelistItem(number, name));
        sortWhitelistItems();
        saveAllToPrefs();
        adapter.updateFullList(whitelistItems);
        updateEmptyView();
    }

    private void removeNumberFromWhitelist(WhitelistItem item, int position) {
        whitelistItems.remove(item);
        saveAllToPrefs();
        adapter.updateFullList(whitelistItems);
        updateEmptyView();
        Toast.makeText(this, R.string.removido, Toast.LENGTH_SHORT).show();
    }

    private void saveAllToPrefs() {
        SharedPreferences numbersPrefs = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
        SharedPreferences namesPrefs = getSharedPreferences("ContactNames", Context.MODE_PRIVATE);
        
        Set<String> numbersSet = new HashSet<>();
        SharedPreferences.Editor nameEditor = namesPrefs.edit();
        nameEditor.clear();
        
        for (WhitelistItem item : whitelistItems) {
            numbersSet.add(item.number);
            nameEditor.putString(item.number, item.name);
        }
        
        numbersPrefs.edit().putStringSet("whitelist", numbersSet).apply();
        nameEditor.apply();
    }

    private void updateEmptyView() {
        txtEmpty.setVisibility(whitelistItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // --- Modelo ---
    private static class WhitelistItem {
        String number;
        String name;
        WhitelistItem(String number, String name) {
            this.number = number;
            this.name = name;
        }
    }

    // --- Adapter Interno ---
    private static class WhitelistAdapter extends RecyclerView.Adapter<WhitelistAdapter.ViewHolder> {
        private final List<WhitelistItem> allItems;
        private final List<WhitelistItem> filteredItems;
        private final OnRemoveListener removeListener;
        private String currentQuery = "";

        interface OnRemoveListener {
            void onRemove(WhitelistItem item, int position);
        }

        WhitelistAdapter(List<WhitelistItem> items, OnRemoveListener listener) {
            this.allItems = new ArrayList<>(items);
            this.filteredItems = new ArrayList<>(items);
            this.removeListener = listener;
        }

        void updateFullList(List<WhitelistItem> items) {
            this.allItems.clear();
            this.allItems.addAll(items);
            filter(currentQuery);
        }

        void filter(String query) {
            currentQuery = query.toLowerCase().trim();
            filteredItems.clear();
            if (currentQuery.isEmpty()) {
                filteredItems.addAll(allItems);
            } else {
                for (WhitelistItem item : allItems) {
                    if (item.name.toLowerCase().contains(currentQuery) || 
                        item.number.contains(currentQuery)) {
                        filteredItems.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_whitelist, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WhitelistItem item = filteredItems.get(position);
            holder.txtContactName.setText(item.name);
            holder.txtPhoneNumber.setText(item.number);
            holder.btnRemove.setOnClickListener(v -> {
                int currentPos = holder.getBindingAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    removeListener.onRemove(item, currentPos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtContactName;
            TextView txtPhoneNumber;
            View btnRemove;

            ViewHolder(View itemView) {
                super(itemView);
                txtContactName = itemView.findViewById(R.id.txtContactName);
                txtPhoneNumber = itemView.findViewById(R.id.txtPhoneNumber);
                btnRemove = itemView.findViewById(R.id.btnRemove);
            }
        }
    }
}
