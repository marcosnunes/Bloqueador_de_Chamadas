package com.bloqueadordechamadas;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Serviço de triagem de chamadas com “logs” aprimorados e lógica robusta.
 */
public class MyCallScreeningService extends CallScreeningService {

    private static final String TAG = "CallScreening";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        try {
            // 1. Verificação de Direção
            if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
                Log.i(TAG, "=> Ignorando chamada efetuada (saída).");
                return;
            }

            // 2. Verificação do Switch
            SharedPreferences prefs = getSharedPreferences("AllowedNumbers", Context.MODE_PRIVATE);
            boolean isBlockerEnabled = prefs.getBoolean("isBlockerEnabled", false);

            if (!isBlockerEnabled) {
                Log.i(TAG, "=> Proteção desligada no app. Permitindo chamada.");
                respondWithAllowance(callDetails);
                return;
            }

            // 3. Obtenção do Número
            Uri handle = callDetails.getHandle();
            if (handle == null) {
                Log.w(TAG, "=> Handle nulo. Permitindo por segurança.");
                respondWithAllowance(callDetails);
                return;
            }

            String incomingNumber = handle.getSchemeSpecificPart();
            Log.i(TAG, "=> Chamada recebida de: " + incomingNumber);

            if (incomingNumber == null || incomingNumber.isEmpty()) {
                Log.w(TAG, "=> Número vazio. Permitindo por segurança.");
                respondWithAllowance(callDetails);
                return;
            }

            // 4. Verificação da Lista Branca
            Set<String> allowedSet = prefs.getStringSet("whitelist", new HashSet<>());
            boolean numberFoundInWhitelist = false;

            String countryCode = Locale.getDefault().getCountry();
            for (String allowedNumber : allowedSet) {
                // areSamePhoneNumber é mais robusto e não depreciado para comparação de números
                if (PhoneNumberUtils.areSamePhoneNumber(incomingNumber, allowedNumber, countryCode)) {
                    numberFoundInWhitelist = true;
                    break;
                }
            }

            // 5. Decisão Final
            if (numberFoundInWhitelist) {
                Log.i(TAG, "=> NÚMERO PERMITIDO (Lista Branca). Deixando tocar.");
                respondWithAllowance(callDetails);
            } else {
                Log.i(TAG, "=> NÚMERO NÃO PERMITIDO. Bloqueando chamada.");
                respondWithBlock(callDetails);
            }

        } catch (Exception e) {
            Log.e(TAG, "=> ERRO NO SERVIÇO: " + e.getMessage(), e);
            respondWithAllowance(callDetails);
        }
    }

    private void respondWithAllowance(Call.Details callDetails) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(callDetails, response);
    }

    private void respondWithBlock(Call.Details callDetails) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true) // Forte para garantir bloqueio imediato
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build();
        respondToCall(callDetails, response);
    }
}
