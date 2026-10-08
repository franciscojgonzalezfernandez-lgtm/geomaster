# GEOMASTER — Product Requirements Document v0.1

Oct 8, 2026 · @Javi

## Resumen ejecutivo

GEOMASTER (geo-masterizer.com) mide y mejora la visibilidad de una marca en las respuestas de ChatGPT, Gemini y Claude para el mercado suizo y DACH, en EN, DE, FR y ES. La beta cerrada arranca en diciembre de 2026 con rideflumserberg.ch como primer caso real, y el producto debe alcanzar el break-even con unos 5 clientes de pago sin superar nunca CHF 400/mes de pérdida.

El producto se inspira en GenScore (mercado hispanohablante) y se diferencia en cuatro puntos:

- **Multilingüe y local desde el día 1:** idioma + país + ciudad como parámetros de cada medición, interfaz en 4 idiomas.
- **Rigor estadístico visible:** cada métrica lleva intervalo de confianza; las muestras se acumulan en ventana móvil.
- **Bucle de acción verificado:** recomendaciones con evidencia, artefactos generados con RAG y verificación del impacto en el siguiente escaneo.
- **Coste unitario controlado:** caché entre clientes, límites duros por plan y un guardián de presupuesto.

GEOMASTER es a la vez un producto monetizado y la pieza principal del portfolio de ingeniería AI-first de su autor: desarrollo agéntico, tres APIs de LLM con búsqueda, agentes y RAG en producción.

| Campo | Valor |
| --- | --- |
| Producto | GEOMASTER |
| Dominio | geo-masterizer.com |
| Responsable | Francisco Javier González Fernández (Gonzalez Fernandez Snowball Effect) |
| Versión del documento | v0.1, borrador para revisión |
| Repositorio | Privado; case study público o acceso bajo demanda |

## Problema y oportunidad

Cuando alguien pide una recomendación a un asistente de IA, la respuesta nombra dos o tres marcas y la decisión se toma ahí, sin visitar ninguna web. Una marca que no aparece no pierde una posición: queda fuera de la conversación, y hoy no tiene forma fiable de saberlo.

**Problemas del cliente:**

- No sabe si ChatGPT, Gemini o Claude la mencionan, en qué posición ni frente a qué competidores.
- Las respuestas no son deterministas: una consulta suelta no es un dato, y las herramientas actuales rara vez muestran el margen de error.
- En Suiza el mismo negocio compite en DE, FR, IT y EN; la visibilidad cambia por idioma y región.
- Aunque sepa que no aparece, no sabe qué hacer ni si sus cambios funcionaron.

**Oportunidad:** las herramientas GEO existentes se centran en un solo idioma o mercado. Una pyme suiza necesita una herramienta asequible y orientada a la acción; una gran empresa necesita medir varias marcas, países e idiomas con rigor, gobernanza y seguridad. GEOMASTER ocupa ese hueco y usa el propio negocio del autor como prueba de valor.

## Usuarios objetivo

El ICP combina dos segmentos: pymes suizas de turismo, servicios locales y profesionales (con los consultores que las asesoran) y grandes empresas con varias marcas, mercados o idiomas en DACH; las agencias llegan después del lanzamiento.

| Persona | Quién es | Trabajo que quiere resolver | Plan típico |
| --- | --- | --- | --- |
| Dueña de pyme local | Escuela de deportes, hotel, clínica, despacho en CH | "¿Me recomienda la IA cuando buscan mi servicio en mi zona?" | Free → Starter |
| Responsable de marketing in-house | Pyme con web multilingüe | Medir por idioma y motor, priorizar contenido, reportar evolución | Pro |
| Consultor de marketing | Freelance con 2–5 clientes | Diagnosticar clientes y demostrar mejoras con datos | Pro |
| Empresa que quiere acompañamiento | Pyme sin equipo de contenido propio | Datos + plan de acción revisado por un experto | Premium (Consultor) |
| Head of Digital / SEO en gran empresa | Empresa con varias marcas, países e idiomas | Medir cuota de voz frente a rivales por mercado, gobernar el contenido y reportar a dirección | Enterprise |
| Equipo de compras y seguridad de gran empresa | Procurement, IT, protección de datos | Contrato, factura, SSO, DPA y SLA antes de firmar | Enterprise |
| Visitante anónimo | Llega por buscador o redes | "¿Aparezco en ChatGPT?" en 30 segundos | Herramienta gratis |

**Mercados:** Suiza (DE, FR, EN) como foco, Alemania y Austria (DE) como extensión, España (ES) como mercado secundario y escaparate de portfolio. Italiano (Tesino) queda en el backlog.

## Objetivos y métricas de éxito

El objetivo de negocio es llegar a 5 clientes de pago (break-even operativo) antes del 30 de junio de 2027 con una pérdida mensual nunca superior a CHF 400.

| Tipo | Métrica | Objetivo |
| --- | --- | --- |
| Negocio | Clientes de pago | ≥ 5 a 2027-06-30; ≥ 15 a 2027-12-31 |
| Negocio | Resultado mensual (ingresos − costes) | ≥ −CHF 400 todos los meses |
| Crecimiento | Conversión comprobación gratis → registro | ≥ 10 % |
| Crecimiento | Conversión Free → pago (90 días) | ≥ 5 % |
| Producto | Activación: escaneo Free completado tras registro | ≥ 80 % |
| Producto | Recomendaciones marcadas como hechas (clientes de pago, 30 días) | ≥ 2 por cliente |
| Producto | Churn mensual de pago | ≤ 5 % |
| Calidad | Escaneos completados sin error | ≥ 99 % |
| Calidad | Extractor de menciones (suite de evals) | Precisión ≥ 95 %, recall ≥ 90 % |
| Coste | Coste medio por respuesta | ≤ CHF 0,05 |
| Portfolio | Case study público + ADRs + evals documentados | Al lanzamiento público |

**Objetivo de dogfooding:** mejorar el GEO Score de rideflumserberg.ch y su tasa de mención (línea base de GenScore: 20 %, escaneo del 20 de septiembre de 2026) durante la temporada 2026/27.

## Benchmark competitivo

GenScore es la referencia funcional: GEOMASTER replica su bucle medir → recomendar → generar → verificar y lo mejora en idioma, rigor y fiabilidad. Otterly, Profound y Peec AI son competidores internacionales a vigilar en el blog de comparativas.

| Capacidad | GenScore (observado 2026-10-08) | GEOMASTER |
| --- | --- | --- |
| Motores | ChatGPT, Gemini, Claude | Igual; Perplexity, Copilot y AI Overviews en backlog |
| Idiomas y mercados | ES, un mercado por proyecto | EN, DE, FR, ES; país + región por proyecto |
| GEO Score | 5 ejes: Presencia, Prominencia, Cuota de voz, Autoridad, Diagnóstico técnico | Mismos 5 ejes + intervalo de confianza |
| Prompts | Agrupados en 6 temas de intención | Mismos temas, por idioma, generados por agente y editables |
| Recomendaciones | Prioridad, impacto/esfuerzo/confianza, evidencia literal, prompts afectados, "+X pt" | Igual + verificación del impacto real y rango de puntos en vez de techo optimista |
| Artefactos | Comparativa, brief, entradilla, FAQ, schema (Pro) | Igual + `llms.txt`, en 4 idiomas, generados con RAG |
| Herramienta gratis | 1 pregunta en vivo a ChatGPT, sin registro | 3 motores × 4 idiomas = 12 landings, resultado compartible |
| Blog | 5 clústeres, RSS | Mismos clústeres en 4 idiomas + clúster GEO DACH |
| Precio de entrada | Starter 45 € (promo 19 €/mes) | Starter CHF 59/mes |
| Fiabilidad | Último escaneo del proyecto observado "con errores" | Reintentos idempotentes, objetivo ≥ 99 % de escaneos completos |

GenScore tiene una incoherencia pública: su página de precios dice que el plan Free usa 1 motor, pero su herramienta gratis promete que el escaneo Free cubre los 3. GEOMASTER mantiene una única fuente de verdad para los límites de cada plan.

Fuentes: [panel de GenScore](https://www.genscore.es/dashboard/projects/ac4ffcf3-b7a9-4769-b893-59a7934f4b63), [recomendaciones](https://www.genscore.es/dashboard/projects/ac4ffcf3-b7a9-4769-b893-59a7934f4b63/recommendations), [precios](https://www.genscore.es/pricing), [herramienta gratis](https://www.genscore.es/gratis/aparece-mi-marca-en-chatgpt), [blog](https://www.genscore.es/blog).

## Alcance por fases

Con unas 10 horas semanales y 9 semanas hasta la beta, la beta cubre solo el bucle principal; facturación, herramienta gratis y blog llegan con el lanzamiento público. Nada se descarta: lo que no entra en una fase queda en el backlog y la arquitectura lo prevé.

**Beta cerrada (desde 2026-12-14, gratuita por invitación):**

- Registro e inicio de sesión, creación de proyecto (dominio, idiomas, país/región).
- Agente de onboarding: crawl de la web, categoría, competidores y set de prompts editables.
- Escaneo semanal en 3 motores, modo con búsqueda, extracción estructurada.
- GEO Score con intervalos de confianza y vistas Visión general, Prompts, Competidores y Páginas citadas.
- Recomendaciones con evidencia, ciclo de vida completo y verificación de impacto.
- Artefactos: brief de contenido, bloque de FAQ y schema JSON-LD.
- Auditoría web ligera.
- Interfaz en EN y DE; ES y FR con traducción revisada antes del lanzamiento.
- Guardián de presupuesto y backoffice de costes.
- Usuarios: rideflumserberg.ch + 5–10 pymes invitadas.

**Lanzamiento público (objetivo 2027-02-15):**

- Stripe con los 4 planes, CHF y EUR, facturación mensual y anual, precio early-bird.
- Herramienta gratis: 12 landings (3 motores × 4 idiomas).
- Blog en 4 idiomas con al menos 8 artículos semilla y RSS.
- Resto de artefactos: comparativa, entradilla, `llms.txt`.
- Análisis de brechas con RAG (plan Pro y superior).
- Notificaciones por email al terminar cada escaneo y resumen semanal.
- Interfaz completa en EN, DE, FR, ES.

**Backlog aplazado (previsto en la arquitectura):**

| Ítem | Motivo del aplazamiento |
| --- | --- |
| Detector de datos falsos sobre la marca (RAG) | Alto valor; requiere el índice de la web estable |
| Modo "memoria del modelo" (sin búsqueda) | Duplica coste; útil como diagnóstico avanzado |
| Escaneo diario como add-on | Coste: \~9.000 respuestas/mes en un plan de 100 prompts |
| Italiano (Tesino) | Quinto idioma tras validar los cuatro primeros |
| Perplexity, Copilot, Google AI Overviews / AI Mode | Nuevos proveedores y costes |
| Detección de oportunidades de prompt y competidores emergentes | Necesita histórico |
| Exportar plan en PDF, informes white-label para agencias | Llega con el segmento agencias |
| Alertas, integración con Slack y GA4 | Tras validar el núcleo |
| API pública y multiusuario avanzado | Tras 15 clientes de pago |
| Add-ons: packs de prompts, idiomas extra | Tras observar el uso real |

## Requisitos funcionales

Cada requisito lleva un identificador estable y la fase en que entra (B = beta, L = lanzamiento), para trazarlo después en las specs y las tareas.

### Cuentas y proyectos

- **FR-ACC-01 (B)** Registro e inicio de sesión con enlace mágico por email y con Google.
- **FR-ACC-02 (B)** Un proyecto = un dominio, uno o varios idiomas de medición (EN, DE, FR, ES) y un país + región opcional.
- **FR-ACC-03 (L)** Usuarios por cuenta según plan: Starter 1, Pro 3, Premium 10.
- **FR-ACC-04 (B)** El usuario elige el idioma de la interfaz con independencia de los idiomas de medición.

### Agente de onboarding

- **FR-ONB-01 (B)** A partir del dominio, el agente rastrea la web, infiere categoría, oferta, ubicación y propuesta de valor, y las muestra para que el usuario las confirme.
- **FR-ONB-02 (B)** Sugiere competidores (Free 3, Starter 5, Pro 10, Premium 15) editables por el usuario.
- **FR-ONB-03 (B)** Genera el set de prompts por idioma, repartido en 6 temas: reseñas y opiniones, precio y planes, casos de uso, alternativas, cómo hacer, comparación.
- **FR-ONB-04 (B)** El usuario edita, borra y añade prompts propios dentro del límite del plan.
- **FR-ONB-05 (B)** El onboarding completo tarda menos de 3 minutos.

### Motor de escaneo

- **FR-SCN-01 (B)** Cada escaneo ejecuta prompt × motor × idioma usando el modelo por defecto de la app de consumo de cada proveedor, con búsqueda web activada.
- **FR-SCN-02 (B)** Frecuencia semanal; Premium añade 2 ejecuciones semanales en 25 prompts clave.
- **FR-SCN-03 (B)** El primer escaneo de un proyecto ejecuta 2 muestras por prompt para la línea base.
- **FR-SCN-04 (B)** Caché compartida: el mismo prompt, motor, idioma, ubicación y día se ejecuta una sola vez aunque lo sigan varias cuentas.
- **FR-SCN-05 (B)** Reintentos idempotentes por respuesta; un escaneo parcial se completa en reintentos sin repetir lo ya obtenido.
- **FR-SCN-06 (B)** Se guarda la respuesta literal, las URLs citadas, el modelo y la versión, las búsquedas realizadas y el coste real.
- **FR-SCN-07 (B)** Al mostrar respuestas de Gemini con grounding, la interfaz muestra las Google Search Suggestions que exige Google.

### Extracción y análisis

- **FR-EXT-01 (B)** Un extractor con salida estructurada detecta en cada respuesta: marcas mencionadas, posición de cada una, sentimiento por marca, dominios y URLs citados.
- **FR-EXT-02 (B)** Resolución de entidades: variantes del nombre de una marca ("Ride Flumserberg", "rideflumserberg.ch") se unifican.
- **FR-EXT-03 (B)** El extractor se valida con una suite de evals versionada antes de cada cambio de modelo o prompt.
- **FR-EXT-04 (L)** Análisis de brechas con RAG: compara el contenido del cliente con las páginas que las IA citan para sus competidores.

### Dashboard

- **FR-DSH-01 (B)** Visión general: GEO Score con intervalo y tendencia, desglose de los 5 ejes, posicionamiento por motor, panorámica competitiva, oportunidades.
- **FR-DSH-02 (B)** Prompts: visibilidad del conjunto, temas con sentimiento, detalle por prompt con la respuesta literal de cada motor.
- **FR-DSH-03 (B)** Competidores: tasa de mención, puesto medio y cuota de voz, por motor e idioma.
- **FR-DSH-04 (B)** Páginas citadas: dominios y URLs citados, propios frente a terceros.
- **FR-DSH-05 (B)** Filtros globales por motor, idioma, tema y periodo.
- **FR-DSH-06 (L)** El plan Free ve su foto actual sin tendencia; los planes de pago ven la evolución.

### Recomendaciones

- **FR-REC-01 (B)** Cada recomendación incluye: título accionable, tipo, prioridad, impacto, esfuerzo, confianza, rango de puntos potenciales, "por qué importa", "empieza por aquí", prompts afectados con motor y ganador, dominios citados, supuestos y evidencia literal.
- **FR-REC-02 (B)** Tipos: entrar en fuentes citadas, cerrar brecha con competidor, crear FAQ, contenido comparativo, amplificar patrón positivo, aumentar prominencia, aumentar visibilidad, bloque de cita, seguir competidor emergente, mejora técnica.
- **FR-REC-03 (B)** Ciclo de vida: Abierta → En curso → Hecha → Verificada o Sin impacto, con el número de escaneos que lleva abierta.
- **FR-REC-04 (B)** Al marcarla como hecha, el siguiente escaneo mide el cambio en sus prompts afectados y lo muestra antes/después.
- **FR-REC-05 (B)** Las recomendaciones se generan en el idioma de la interfaz y se refieren al idioma de medición afectado.
- **FR-REC-06 (B)** El plan Free recibe 3 acciones; Starter un bucle básico; Pro y Premium el bucle completo.

### Artefactos

- **FR-ART-01 (B)** Desde una recomendación, el usuario genera el artefacto asociado: brief de contenido, bloque de FAQ, schema JSON-LD (B); comparativa, entradilla, `llms.txt` (L).
- **FR-ART-02 (B)** Cada artefacto se genera con RAG sobre la evidencia de la recomendación, el contenido indexado de la web del cliente y las páginas citadas por las IA; nunca desde cero.
- **FR-ART-03 (B)** Se genera en el idioma de medición elegido; el usuario puede editarlo, copiarlo y regenerarlo.
- **FR-ART-04 (L)** Límite mensual: Starter 0, Pro 50, Premium 200.

### Auditoría web

- **FR-AUD-01 (B)** Comprueba: acceso de bots de IA en robots.txt, existencia de `llms.txt`, datos estructurados, sitemap, contenido visible sin JavaScript, `hreflang`.
- **FR-AUD-02 (B)** Alimenta el eje "Diagnóstico técnico" del GEO Score y genera recomendaciones de tipo técnico.

### Herramienta gratis

- **FR-FREE-01 (L)** 12 landings localizadas: "¿Aparece mi marca en ChatGPT / Gemini / Claude?" × EN, DE, FR, ES.
- **FR-FREE-02 (L)** Sin registro: el usuario introduce su dominio; el sistema genera una pregunta real de su categoría, la lanza en vivo y muestra pregunta, respuesta literal con menciones resaltadas, si aparece y qué marcas nombra.
- **FR-FREE-03 (L)** Explica que una consulta es una muestra con margen de error y lleva al escaneo Free.
- **FR-FREE-04 (L)** Resultado compartible con URL propia e imagen OG; email opcional para recibir el informe.
- **FR-FREE-05 (L)** Protección anti-abuso con Cloudflare Turnstile y límite por IP y por dominio.

### Blog

- **FR-BLOG-01 (L)** Blog en 4 idiomas con clústeres: Fundamentos GEO, Metodología y medición, Playbooks, GEO por sector, GEO DACH, Comparativas.
- **FR-BLOG-02 (L)** Cada artículo enlaza a la herramienta gratis, lleva `hreflang`, datos estructurados y RSS.
- **FR-BLOG-03 (L)** Los artículos se redactan con apoyo de IA y revisión humana; se miden con el propio GEOMASTER.

### Planes y facturación

- **FR-BIL-01 (L)** Stripe Checkout y portal de cliente: alta, cambio y cancelación de plan sin intervención manual.
- **FR-BIL-02 (L)** Precios en CHF y EUR; facturación mensual o anual (2 meses gratis); early-bird −40 % durante 6 meses para los primeros 20 clientes.
- **FR-BIL-03 (L)** Los límites de cada plan viven en una única configuración que usan producto, landing y página de precios.
- **FR-BIL-04 (L)** Premium incluye la reserva de una sesión mensual de 90 minutos con el consultor.

### Notificaciones

- **FR-NOT-01 (L)** Email al completar cada escaneo con el cambio del GEO Score y las nuevas recomendaciones.
- **FR-NOT-02 (L)** Resumen semanal y aviso si un escaneo falla.

### Backoffice y guardián de presupuesto

- **FR-OPS-01 (B)** Panel interno con coste real por respuesta, por proyecto, por cuenta y por proveedor.
- **FR-OPS-02 (B)** Guardián de presupuesto: si la previsión del mes supera el límite de pérdida, pausa primero la herramienta gratis, luego los escaneos Free nuevos, nunca los escaneos de pago.
- **FR-OPS-03 (B)** Límites duros de búsquedas por llamada y de respuestas por plan.

## Metodología de medición

El GEO Score (0–100) es una media ponderada de cinco ejes, calculada sobre una ventana móvil de los últimos 4 escaneos y publicada siempre con su intervalo de confianza del 95 %. Los pesos son una hipótesis inicial que se calibra durante la beta.

```latex
\text{GEO} = 0.35\,P + 0.20\,R + 0.20\,V + 0.15\,A + 0.10\,T
```

| Eje | Símbolo | Definición (0–100) | Peso |
| --- | --- | --- | --- |
| Presencia | P | Respuestas que mencionan la marca ÷ respuestas totales | 35 % |
| Prominencia | R | Media, sobre las menciones, de max(0, 1 − (posición − 1) ÷ 5) | 20 % |
| Cuota de voz | V | Menciones de la marca ÷ menciones de marca + competidores | 20 % |
| Autoridad | A | Respuestas que citan el dominio propio ÷ respuestas con citas | 15 % |
| Diagnóstico técnico | T | Comprobaciones de auditoría superadas, ponderadas | 10 % |

**Reglas de medición:**

- **Unidad:** una respuesta = prompt × motor × idioma × ejecución, con búsqueda web activada.
- **Ventana móvil:** los 4 últimos escaneos semanales se tratan como muestras repetidas; así hay intervalos sin multiplicar llamadas.
- **Intervalos:** Wilson para las proporciones (P, V, A); bootstrap para el GEO Score compuesto.
- **Suficiencia:** un desglose con menos de 30 respuestas se muestra como "datos insuficientes" en vez de un número.
- **Puntos potenciales:** cada recomendación muestra un rango (bajo–alto) calculado con los prompts afectados, nunca un techo único.
- **Transparencia:** cada número enlaza a las respuestas literales que lo sostienen.
- **Versionado:** cada escaneo guarda la versión de la fórmula, del extractor y de los modelos usados; un cambio de versión se marca en la tendencia.

## Planes y precios

GEOMASTER cobra por prompts × motores × idiomas × frecuencia, con cuatro planes de autoservicio, un plan Enterprise a medida y escaneo semanal; no hay prueba gratuita de Pro porque el escaneo Free cumple esa función.

|  | Free | Starter | Pro | Premium (Consultor) |
| --- | --- | --- | --- | --- |
| Precio (CHF/mes) | 0 | 59 | 169 | 690 |
| Dominios | 1 | 1 | 3 | 5 |
| Prompts (prompt × idioma) | 12 | 30 | 100 | 250 |
| Motores | 1 (ChatGPT) | 3 | 3 | 3 |
| Idiomas de medición | 1 | 2 | 4 | 4 |
| Frecuencia | 1 escaneo | Semanal | Semanal | Semanal + 2×/semana en 25 prompts clave |
| Competidores por dominio | 3 | 5 | 10 | 15 |
| Tendencia e histórico | No | 12 meses | Sin límite | Sin límite |
| Recomendaciones | 3 acciones | Bucle básico | Bucle completo | Bucle completo |
| Artefactos/mes | 0 | 0 | 50 | 200 |
| Análisis de brechas (RAG) | No | No | Sí | Sí |
| Usuarios | 1 | 1 | 3 | 10 |
| Consultoría | No | No | No | 90 min/mes |

**Reglas comerciales:**

- Precios equivalentes en EUR para clientes de la UE.
- Anual: 2 meses gratis.
- Early-bird: −40 % durante 6 meses para los primeros 20 clientes de pago.
- Herramienta gratis: anónima, 1 pregunta en vivo a un motor.
- Add-ons (backlog): packs de prompts, idiomas extra, escaneo diario.

**Enterprise (a medida, tras el lanzamiento):** para grandes empresas, con dominios, marcas, prompts y mercados a medida; SSO (SAML/OIDC), roles y permisos, registro de auditoría, facturación por factura anual, DPA y SLA de disponibilidad, exportación de datos y onboarding acompañado. El precio se negocia con un mínimo orientativo de CHF 1.500/mes; la arquitectura prevé multi-marca y SSO desde el principio.

## Modelo económico

Con una pérdida máxima de CHF 400/mes, los costes fijos se limitan a unos CHF 130 y el embudo gratuito a lo que quede de presupuesto; el break-even operativo llega con 5 clientes de pago. El sueldo del autor no entra en este cálculo.

**Coste unitario.** Una respuesta se presupuesta en CHF 0,05: unas 2 búsquedas web (OpenAI $10 por 1.000 llamadas, Claude $10 por 1.000 búsquedas, Gemini 3 $14 por 1.000 consultas tras 5.000 gratis al mes), unos 8k tokens de entrada y 1k de salida en el modelo de consumo, y la extracción con un modelo barato como Claude Haiku 5.5 ($0,10 / $0,50 por millón de tokens). Cada escaneo suma CHF 0,25 por generar recomendaciones y cada artefacto CHF 0,10. Stripe: \~3 % + CHF 0,30.

| Plan | Respuestas/mes | Coste API + infra + Stripe (CHF) | Consultoría (CHF) | Margen (CHF) | Margen % |
| --- | --- | --- | --- | --- | --- |
| Free (único) | 24 | 1,75 | — | −1,75 | — |
| Starter (59) | \~390 | 23,6 | — | 35,4 | 60 % |
| Pro (169) | \~1.300 | 83,6 | — | 85,4 | 51 % |
| Premium (690) | \~3.570 | 234 | 150 (90 min × CHF 100) | 306 | 44 % |

**Presupuesto mensual sin ingresos (techo CHF 400):**

| Partida | Beta (dic 2026–feb 2027) | Tras el lanzamiento |
| --- | --- | --- |
| Infraestructura (hosting, base de datos, colas, email, monitorización, dominio) | 100 | 100 |
| Agentes de revisión de PR (API) | 30 | 30 |
| Dogfooding rideflumserberg.ch (40 prompts × 3 motores × 4 idiomas) | 30 | 30 |
| 8 usuarios beta invitados (nivel Starter) | 190 | — |
| Herramienta gratis (\~1.000 comprobaciones × CHF 0,07) | — | 70 |
| Escaneos Free (máx. 90 × CHF 1,75) | — | 158 |
| **Total** | **350** | **388** |

**Break-even:** con una mezcla 60 % Starter, 30 % Pro y 10 % Premium, el margen medio es de CHF 77,5 por cliente; 388 ÷ 77,5 = 5 clientes de pago. Si el 5 % de los Free convierte, captar un cliente cuesta unos CHF 35 en escaneos gratis y se recupera en menos de un mes.

**Regla del guardián de presupuesto:** gasto del embudo gratuito ≤ CHF 400 + margen de los clientes de pago − costes fijos. Así el embudo crece con los ingresos y la pérdida nunca supera CHF 400.

**Palancas de ahorro:** caché entre clientes, ventana móvil de muestras, Batch API (−50 % en tokens de Claude) para escaneos programados, la cuota gratuita de Gemini para la herramienta gratis de Gemini, y límites de búsquedas por llamada.

Fuentes: [precios de Claude](https://platform.claude.com/docs/ko/about-claude/pricing), [precios de OpenAI API](https://pricingsaas.com/companies/openai.api), [grounding de Gemini](https://parallel.ai/ai/articles/gemini-google-search-grounding-vs-parallel). Estimación de consumo de tokens por respuesta pendiente de validar en la beta.

## Requisitos no funcionales

La fiabilidad del escaneo y el control de costes son los dos atributos que más pesan: son los fallos visibles de la competencia y el riesgo principal del negocio.

| ID | Atributo | Requisito |
| --- | --- | --- |
| NFR-01 | Rendimiento | Comprobación gratis < 30 s (p95); escaneo Pro completo < 15 min; dashboard < 2 s (p95) |
| NFR-02 | Fiabilidad | ≥ 99 % de escaneos completos; reintentos idempotentes; un proveedor caído no bloquea los otros dos |
| NFR-03 | Disponibilidad | 99,5 % mensual de la aplicación web |
| NFR-04 | Coste | Coste real registrado por respuesta; límites duros por plan; guardián de presupuesto activo |
| NFR-05 | Seguridad | Claves de API solo en servidor; aislamiento entre cuentas; OWASP Top 10; dependencias revisadas por agentes en cada PR |
| NFR-06 | Privacidad | Datos alojados en CH o UE; exportación y borrado de cuenta en autoservicio |
| NFR-07 | Accesibilidad | WCAG 2.1 AA |
| NFR-08 | i18n | Interfaz, emails, landings y blog en EN, DE, FR, ES; URLs localizadas con `hreflang`; formatos de fecha, número y moneda por locale |
| NFR-09 | SEO/GEO propio | Lighthouse ≥ 90 en Rendimiento, Accesibilidad, Prácticas recomendadas y SEO en todas las páginas públicas, en móvil y escritorio, verificado en CI; páginas renderizadas en servidor, datos estructurados, `llms.txt`, sitemap por idioma |
| NFR-10 | Observabilidad | Trazas por escaneo y por llamada a LLM; alertas por tasa de error y por desviación de coste |
| NFR-11 | Calidad del código | Spec Driven Development; revisión de PR por agentes (resumen, vulnerabilidades, calidad); tests y evals en CI |
| NFR-12 | Portabilidad de modelos | Capa de proveedores intercambiable: cambiar de modelo es configuración, no código |
| NFR-13 | Reproducibilidad | Cada métrica es trazable hasta las respuestas literales y las versiones de modelo, prompt y fórmula |

## IA, agentes y RAG

GEOMASTER usa IA en tres capas: tres APIs de LLM con búsqueda como objeto de medición, tres agentes que trabajan sobre sus respuestas y un índice RAG que ancla recomendaciones y artefactos en contenido real.

&#91;embedded content: flujo de un escaneo · 3 agentes, 3 motores, 1 índice RAG\]

El extractor convierte texto libre en datos; el índice RAG, alimentado por el crawl del onboarding y por las URLs citadas, evita que un artefacto se escriba desde cero.

| Componente | Técnica | Modelo de partida (se decide en ADR) |
| --- | --- | --- |
| Agente de onboarding | Agente con herramientas: crawl, búsqueda, generación de prompts | Claude Sonnet 5.5 |
| Motores medidos | APIs oficiales con búsqueda web o grounding | Modelo por defecto de cada app de consumo |
| Extractor | Salida estructurada con esquema y suite de evals | Modelo barato (Claude Haiku 5.5 o equivalente) |
| Agente de acciones | Agente con acceso a métricas, evidencia e índice RAG | Claude Sonnet 5.5 |
| Generador de artefactos | Generación aumentada con recuperación | Claude Sonnet 5.5 |
| Índice RAG | Embeddings + búsqueda vectorial por proyecto e idioma | A decidir en arquitectura |

**Desarrollo agéntico del propio producto:** Spec Driven Development (PRD → specs → tareas), implementación con Claude Code, y agentes en GitHub Actions que resumen cada PR, buscan vulnerabilidades y puntúan su calidad; los cambios rutinarios con calidad superior al 90 % pueden aprobarse solos. Las evals del extractor corren en CI como cualquier test.

## Legal y cumplimiento

GEOMASTER factura desde la empresa unipersonal Gonzalez Fernandez Snowball Effect y debe cumplir la nDSG suiza y el RGPD desde la beta. Los puntos fiscales necesitan validación de un asesor antes del lanzamiento.

- **Protección de datos:** nDSG + RGPD; política de privacidad en 4 idiomas; registro de encargados (proveedores de LLM, hosting, Stripe, email); acuerdos de tratamiento firmados.
- **Impressum y condiciones:** Impressum suizo, condiciones de uso y de suscripción, política de cancelación.
- **Cookies:** solo las esenciales por defecto; analítica sin cookies o con consentimiento.
- **Términos de los proveedores de IA:** uso exclusivo de las APIs oficiales, sin scraping de las apps de consumo; mostrar las Google Search Suggestions junto a las respuestas de Gemini con grounding.
- **Transparencia de medición:** el producto explica que mide las APIs con búsqueda, que pueden diferir de lo que ve un usuario concreto en la app.
- **IVA (a validar con asesor):** el IVA suizo solo es obligatorio a partir de CHF 100.000 de facturación anual; las ventas de servicios digitales a consumidores de la UE pueden exigir registro en el OSS de la UE desde el primer euro. Stripe Tax calcula y registra el impuesto.
- **Marca y dominio:** confirmar el registro de geo-masterizer.com y comprobar disponibilidad de la marca "GEOMASTER" en Swissreg y EUIPO antes del lanzamiento.

## Fase de diseño de marca

La fase de diseño fija la identidad de GEOMASTER y su sistema de diseño antes de construir pantallas; dura unas dos semanas y termina con los tokens listos para el código.

**Principios de partida (a validar en la fase):** precisión suiza, datos honestos y claridad para no expertos; el producto debe transmitir rigor de medición sin parecer una herramienta para analistas.

**Entregables:**

- [ ] Plataforma de marca: propósito, promesa, personalidad y posicionamiento frente a GenScore, Peec AI y Otterly.
- [ ] Tono de voz en EN, DE, FR, ES, con glosario de términos GEO por idioma.
- [ ] Logotipo, isotipo y favicon para geo-masterizer.com.
- [ ] Paleta con colores semánticos para métricas (bien, aviso, mal) que cumpla contraste WCAG AA, en modo claro y oscuro.
- [ ] Tipografía con soporte completo de caracteres DE y FR.
- [ ] Lenguaje de visualización de datos: gráficos con intervalos de confianza, comparativas por motor e idioma.
- [ ] Design tokens y librería de componentes.
- [ ] Pantallas clave: landing, herramienta gratis y su resultado, onboarding, Visión general, Prompts, Competidores, Páginas citadas, Recomendaciones, artefacto, precios, blog.
- [ ] Plantillas de email e imagen OG compartible.

## Calendario y fases

La beta cerrada arranca el Dec 14, 2026, el lanzamiento público está previsto para el Feb 15, 2027 y el break-even operativo para el Jun 30, 2027.

&#91;embedded content: calendario de fases · oct 2026 a feb 2027\]

Diseño y arquitectura se solapan una semana: la arquitectura no depende de la identidad visual, solo de los tokens. Cada fase cierra con un entregable revisable: PRD aprobado, sistema de diseño, ADRs, beta en producción.

**Criterios para pasar de beta a lanzamiento:** ≥ 99 % de escaneos completos durante 4 semanas, coste medio por respuesta ≤ CHF 0,05 medido, evals del extractor dentro de umbral, y al menos 3 usuarios beta que pagarían.

## Riesgos y mitigaciones

El riesgo más probable es que el coste real por respuesta supere CHF 0,05; el de más impacto, que las APIs no reflejen lo que ve el usuario en las apps de consumo.

| Riesgo | Probabilidad | Impacto | Mitigación |
| --- | --- | --- | --- |
| Coste por respuesta > CHF 0,05 (más búsquedas o tokens de lo previsto) | Alta | Alto | Medir en la beta; límites de búsquedas por llamada; Batch API; ajustar prompts por plan antes del lanzamiento |
| Las respuestas de la API difieren de las apps de consumo | Media | Alto | Transparencia en el producto; muestreo manual periódico de las apps para comparar |
| Cambios de precio o de modelos de los proveedores | Alta | Medio | Capa de proveedores intercambiable; precios revisables con 30 días de aviso |
| Calendario: 10 h/semana no bastan para la beta del 14 de diciembre | Media | Medio | Alcance de beta reducido al bucle principal; desarrollo agéntico con revisión automática de PR |
| Falsos positivos o negativos en la detección de menciones | Media | Alto | Suite de evals con umbrales en CI; resolución de entidades |
| Abuso de la herramienta gratis | Media | Medio | Turnstile, límites por IP y dominio, guardián de presupuesto |
| Competidores con más recursos entran en DACH | Media | Medio | Foco local y multilingüe, consultoría personal, precio en CHF |
| Registro del IVA de la UE no previsto | Baja | Medio | Stripe Tax y validación con asesor antes de cobrar |

## Supuestos, preguntas abiertas y fuentes

Los números de este PRD descansan en cinco supuestos que la beta debe confirmar; las preguntas abiertas se resuelven en la fase de arquitectura o antes del lanzamiento.

**Supuestos:**

- Coste medio de CHF 0,05 por respuesta, con unas 2 búsquedas y 9k tokens.
- Mezcla de clientes 60 % Starter, 30 % Pro, 10 % Premium.
- Conversión Free → pago del 5 % en 90 días.
- Costes fijos de unos CHF 130/mes.
- Disponibilidad del autor: 10 h/semana, apoyado en desarrollo agéntico.

**Preguntas abiertas:**

- [ ] ¿Qué modelo exacto usa cada app de consumo en cada momento y cómo detectamos sus cambios?
- [ ] ¿Los pesos del GEO Score reflejan bien la realidad? Calibrar con los datos de la beta.
- [ ] ¿Qué 8 pymes se invitan a la beta y con qué criterio?
- [ ] ¿Se registra la marca GEOMASTER en Suiza y en la UE?
- [ ] ¿Qué CMS usa el blog para gestionar 4 idiomas con revisión humana?

**Fuentes** (consultadas el 8 de octubre de 2026):

- [GenScore: visión general del proyecto](https://www.genscore.es/dashboard/projects/ac4ffcf3-b7a9-4769-b893-59a7934f4b63)
- [GenScore: recomendaciones](https://www.genscore.es/dashboard/projects/ac4ffcf3-b7a9-4769-b893-59a7934f4b63/recommendations)
- [GenScore: precios](https://www.genscore.es/pricing)
- [GenScore: herramienta gratis](https://www.genscore.es/gratis/aparece-mi-marca-en-chatgpt)
- [GenScore: blog](https://www.genscore.es/blog)
- [Claude Platform: precios](https://platform.claude.com/docs/ko/about-claude/pricing)
- [OpenAI API: historial de precios (PricingSaaS)](https://pricingsaas.com/companies/openai.api)
- [Gemini: grounding con Google Search (Parallel)](https://parallel.ai/ai/articles/gemini-google-search-grounding-vs-parallel)
- [Gemini: requisito de Google Search Suggestions (The Decoder)](https://the-decoder.com/?p=18631)
