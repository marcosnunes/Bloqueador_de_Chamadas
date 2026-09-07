# Bloqueador de Chamadas (Lista Branca) 🛡️

[![Android SDK](https://img.shields.io/badge/SDK-31%20--%2037-brightgreen.svg)](https://developer.android.com)
[![Material 3](https://img.shields.io/badge/Design-Material%203-blue.svg)](https://m3.material.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Um aplicativo Android moderno e focado em privacidade para triagem de chamadas. O **Bloqueador de Chamadas** permite que você recupere o controle sobre quem pode te ligar, permitindo apenas números da sua lista branca e bloqueando automaticamente spam, telemarketing e números desconhecidos.

## ✨ Funcionalidades

- **🚀 Proteção Ativa (Call Screening):** Utiliza a API oficial de `CallScreeningService` do Android para um bloqueio eficiente e silencioso.
- **🎨 Interface Material 3:** Design moderno, limpo e com suporte total a **Edge-to-Edge** (estilo Samsung OneUI/Google Pixel).
- **🔍 Busca Inteligente:** Encontre rapidamente contatos na sua lista branca pesquisando por nome ou número.
- **🆔 Reconhecimento de Contatos:** Ao adicionar um número manualmente, o app identifica automaticamente se ele já pertence a algum contato na sua agenda.
- **AZ Ordenação Alfabética:** Lista sempre organizada de A a Z para facilitar a navegação.
- **📥 Importação em Lote:** Importe todos os seus contatos existentes para a lista branca com apenas um toque.
- **🔒 Privacidade Total:** Todo o processamento é feito localmente no dispositivo. Nenhum dado é enviado para servidores externos.

## 📸 Screenshots

| Tela Principal | Lista Branca | Adicionar Número |
| :---: | :---: | :---: |
| ![Home](store_assets/graphics/placeholder_screenshot.png) | ![List](store_assets/graphics/placeholder_screenshot.png) | ![Add](store_assets/graphics/placeholder_screenshot.png) |
*(Mockups sugeridos em `store_assets/graphics/`)*

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java (Android Nativo)
- **SDK Alvo:** Android 17 (API 37)
- **SDK Mínima:** Android 12 (API 31)
- **Bibliotecas:** Material Components, ConstraintLayout, RecyclerView, ViewBinding.

## 🚀 Como Compilar e Rodar

1. Clone este repositório:
   ```bash
   git clone https://github.com/marcosnunes/bloqueador-de-chamadas.git
   ```
2. Abra o projeto no **Android Studio**.
3. Certifique-se de que o JDK 17+ está configurado.
4. Clique em **Run** ou compile via terminal:
   ```bash
   ./gradlew assembleDebug
   ```

## 📄 Política de Privacidade

A nossa política de privacidade pode ser acessada em:
[https://marcosnunes.github.io/Bloqueador_de_Chamadas/docs/index.html](docs/index.html)

## ⚖️ Licença

Este projeto está sob a licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.

---
Desenvolvido com ❤️ para a comunidade Android.
