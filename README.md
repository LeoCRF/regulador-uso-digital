# Regulador de Uso Digital 📱

Este é um projeto desenvolvido para fins acadêmicos com o objetivo de auxiliar usuários a monitorar e reduzir o tempo de tela de forma saudável. O aplicativo fornece insights detalhados sobre o uso semanal e sugere metas realistas baseadas no comportamento real do usuário.

## 🚀 Funcionalidades Principais

*   **Monitoramento Semanal:** Diferente de outros apps que focam apenas no dia, este exibe o acumulado dos últimos 7 dias para uma visão real do hábito.
*   **Cálculo de Meta Saudável:** O sistema calcula sua média diária e sugere automaticamente uma redução de 15%, incentivando uma melhora progressiva.
*   **Transparência Total:** Lista absolutamente todos os aplicativos que consumiram tempo na semana (incluindo processos de sistema).
*   **Interface Didática:**
    *   Conversão automática de minutos para horas em tempo real.
    *   Frases explicativas sobre o seu hábito de uso.
    *   Barra de progresso que compara o uso de "Hoje" com o seu limite definido.
*   **Animações Fluídas:** Transições suaves de "queda" (fall down) ao carregar a lista de aplicativos.

## 🛠️ Tecnologias e Dependências

O projeto foi construído utilizando **Kotlin** e segue as melhores práticas de desenvolvimento Android moderno.

### Principais Bibliotecas:

*   **AndroidX Core KTX:** Extensões Kotlin para APIs do sistema.
*   **Material Design:** Componentes visuais modernos e responsivos.
*   **MPAndroidChart:** Utilizada para renderizar os gráficos de barras de uso diário e semanal.
*   **Kotlin Coroutines:** Gerenciamento de tarefas em segundo plano (como a leitura pesada de estatísticas) para garantir que o app não trave.
*   **Gson:** Manipulação e persistência de dados.
*   **UsageStatsManager:** API nativa do Android utilizada para extrair dados reais de tempo de tela.

### Lista Técnica de Dependências (build.gradle):
```gradle
implementation("androidx.core:core-ktx:1.10.1")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.10.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
implementation("com.google.code.gson:gson:2.10.1")
```

## ⚙️ Como Instalar e Testar

Para rodar este projeto em sua máquina local, siga os passos abaixo:

1.  **Clone o repositório:**
    ```bash
    https://github.com/LeoCRF/regulador-uso-digital
    ```
2.  **Abra no Android Studio:**
    *   Selecione `File > Open` e escolha a pasta do projeto.
    *   Aguarde o **Gradle Sync** finalizar (certifique-se de estar conectado à internet).
3.  **Permissões Necessárias:**
    *   Ao abrir o app pela primeira vez, ele solicitará **Acesso aos Dados de Uso**.
    *   Você será redirecionado para as configurações do Android; localize o "Regulador de Uso Digital" e ative a permissão. **Isso é essencial para que os gráficos e tempos apareçam.**
4.  **Uso:**
    *   Navegue pela barra inferior.
    *   Na aba **Apps**, defina um limite em minutos para qualquer aplicativo.
    *   Veja a conversão em horas aparecer instantaneamente.
    *   Clique em **Aplicar** para salvar seu limite.

## 🎓 Contexto Acadêmico

Este projeto foca na **Experiência do Usuário (UX)** e na **Educação Digital**. A lógica de redução de 15% foi implementada para evitar o "choque" de uma mudança brusca de hábito, promovendo uma saúde mental sustentável no uso da tecnologia.

---
**Desenvolvido por:** Leo Matheus
**Disciplina:** Fábrica de Software
**Instituição:** UniGuairacá
