# Plano de Implementação - Limpeza de Alertas e Preparação para Produção

Este plano descreve as correções de avisos (warnings) e otimizações necessárias para garantir que o código siga as melhores práticas do Android e esteja pronto para publicação na Play Store.

## Proposed Changes

### Source Code (Java)

#### [MODIFY] [MainActivity.java](file:///D:/Android/Bloqueador_de_chamadas/app/src/main/java/com/bloqueadordechamadas/MainActivity.java)
- **Otimização de Memória**: Converter `switchBlocker` em variável local dentro do `onCreate`.
- **Melhores Práticas de Prefs**: Substituir `commit()` por `apply()` para operações assíncronas no disco, evitando pequenos travamentos na UI.
- **Correção de Lógica**:
    - Substituir `if` por `switch` no tratamento do resultado da Role.
    - Usar `Objects.equals()` para comparações de String mais seguras.
    - Simplificar a inversão lógica do método `isNumberInWhitelist`.
- **Internacionalização**: Mover os textos de "Status: Vinculado..." e "Status: Não vinculado..." para o arquivo `strings.xml`.
- **Performance de Lista**: Melhorar a eficiência das atualizações de lista (usando `notifyItemRangeInserted` em vez de `notifyDataSetChanged` na importação de contatos).

#### [MODIFY] [MyCallScreeningService.java](file:///D:/Android/Bloqueador_de_chamadas/app/src/main/java/com/bloqueadordechamadas/MyCallScreeningService.java)
- **API Atualizada**: Substituir o método depreciado `PhoneNumberUtils.compare` pelo recomendado `PhoneNumberUtils.areSamePhoneNumber` (usando o código de país padrão do dispositivo).
- **Limpeza**: Corrigir a reatribuição da variável `isAllowed`.

---

### UI Resources (XML)

#### [MODIFY] [activity_main.xml](file:///D:/Android/Bloqueador_de_chamadas/app/src/main/res/layout/activity_main.xml)
- **Internacionalização**: Mover a string hardcoded "Proteção Ativa" para `strings.xml`.

#### [MODIFY] [strings.xml](file:///D:/Android/Bloqueador_de_chamadas/app/src/main/res/values/strings.xml)
- Adicionar as novas strings identificadas durante a limpeza.

## Verification Plan

### Automated Tests
- Executar `./gradlew app:assembleDebug` para garantir que as trocas de métodos (depreciados) não quebraram a compatibilidade.

### Manual Verification
1.  **Status**: Verificar se o texto de status do sistema continua aparecendo corretamente com as novas strings.
2.  **Bloqueio**: Confirmar que a nova API de comparação de números (`areSamePhoneNumber`) continua identificando os contatos da lista branca.
3.  **Logs**: Garantir que não há mais alertas amarelos ou vermelhos nas classes Java no Android Studio.
