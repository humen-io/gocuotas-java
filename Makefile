# Raíz del proyecto Java multi-módulo (válido aunque invoques make desde otro directorio con -f java/Makefile)
JAVA_ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
J := cd "$(JAVA_ROOT)" &&

GRADLEW := $(J) ./gradlew
MVN := $(J) mvn

# Base de la API (sin barra final). Sobrescribí con: make listar-ordenes GOCUOTAS_API_URL=https://...
GOCUOTAS_API_URL ?= https://www.gocuotas.com

# Ventana relativa hacia atrás (solo formato Nd, ej. 30d). make listar-ordenes GOCUOTAS_DELIVERED_RANGE=7d
GOCUOTAS_DELIVERED_RANGE ?= 30d

.PHONY: help all ci clean clean-gradle clean-maven \
	build build-gradle build-maven \
	test test-gradle test-maven \
	javadoc javadoc-gradle javadoc-maven \
	check deps wrapper listar-ordenes buscar-orden reembolsar-orden informacion-comercio listar-liquidaciones informacion-liquidacion liquidaciones-texto-plano informacion-liquidacion-texto-plano

.DEFAULT_GOAL := help

help:
	@echo "GoCuotas Java — objetivos disponibles"
	@echo ""
	@echo "  make all / make ci     Limpiar, compilar (Gradle+Maven, con tests) y javadoc"
	@echo "  make clean             clean Gradle + target/ de Maven"
	@echo "  make build             build Gradle + package Maven (cada uno ejecuta sus tests)"
	@echo "  make test              solo tests: Gradle + Maven"
	@echo "  make javadoc           javadoc Gradle + javadoc Maven"
	@echo "  make check             alias de all"
	@echo ""
	@echo "  make listar-ordenes    GET órdenes (rango Nd, default GOCUOTAS_DELIVERED_RANGE=30d; fechas absolutas vía env)"
	@echo "  make buscar-orden      GET una orden por id (GOCUOTAS_ORDER_ID o env; JWT vía GOCUOTAS_JWT o authenticate)"
	@echo "  make reembolsar-orden  DELETE reembolso (mismo id y auth que buscar-orden)"
	@echo "  make informacion-comercio  GET comercio (API Client V1; GOCUOTAS_COMMERCE_API_KEY)"
	@echo "  make listar-liquidaciones  GET liquidaciones expense_settlements (misma API key)"
	@echo "  make informacion-liquidacion  GET una liquidación por id (GOCUOTAS_LIQUIDACION_ID + API key)"
	@echo "  make liquidaciones-texto-plano  GET liquidaciones CSV (Accept: text/plain; sin jq)"
	@echo "  make informacion-liquidacion-texto-plano  GET CSV de una liquidación por id (GOCUOTAS_LIQUIDACION_ID; sin jq)"
	@echo ""
	@echo "Objetivos individuales:"
	@echo "  clean-gradle clean-maven  build-gradle build-maven  test-gradle test-maven"
	@echo "  javadoc-gradle javadoc-maven  deps  wrapper  listar-ordenes  buscar-orden  reembolsar-orden  informacion-comercio  listar-liquidaciones  informacion-liquidacion  liquidaciones-texto-plano  informacion-liquidacion-texto-plano"
	@echo ""
	@echo "Directorio del módulo: $(JAVA_ROOT)"

all: ci

ci: clean build javadoc
	@echo "CI local completado (Gradle + Maven)."

check: ci

clean: clean-gradle clean-maven

clean-gradle:
	$(GRADLEW) clean --no-daemon

clean-maven:
	$(J) rm -rf target gocuotas-api-common/target gocuotas-redirect/target gocuotas-client/target

build: build-gradle build-maven

build-gradle:
	$(GRADLEW) build --no-daemon

build-maven:
	$(MVN) -q clean package

test: test-gradle test-maven

test-gradle:
	$(GRADLEW) test --no-daemon

test-maven:
	$(MVN) -q test

javadoc: javadoc-gradle javadoc-maven

javadoc-gradle:
	$(GRADLEW) javadoc --no-daemon

javadoc-maven:
	$(MVN) -q javadoc:javadoc

deps:
	@echo "=== Gradle dependencies ==="
	$(GRADLEW) dependencies --no-daemon
	@echo ""
	@echo "=== Maven dependency tree ==="
	$(MVN) -q dependency:tree

wrapper:
	$(J) gradle wrapper --gradle-version 8.7 --no-daemon

# Listado en vivo: authenticate (opcional) → GET .../orders?delivered_start&delivered_end (rango $(GOCUOTAS_DELIVERED_RANGE) salvo env)
listar-ordenes:
	@command -v jq >/dev/null 2>&1 || { echo "listar-ordenes requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	BEARER=""; \
	if [ -n "$$GOCUOTAS_JWT" ]; then \
	  BEARER="$$GOCUOTAS_JWT"; \
	elif [ -n "$$GOCUOTAS_EMAIL" ] && [ -n "$$GOCUOTAS_API_KEY" ]; then \
	  AUTH_JSON=$$(jq -n --arg e "$$GOCUOTAS_EMAIL" --arg p "$$GOCUOTAS_API_KEY" '{email: $$e, password: $$p}'); \
	  BEARER=$$(curl -sS "$$base/api_redirect/v1/authentication" \
	    -H "Content-Type: application/json" -H "Accept: application/json" \
	    -d "$$AUTH_JSON" | jq -er '(.token // .access_token)') \
	    || { echo "No se pudo obtener JWT (revisá GOCUOTAS_EMAIL y GOCUOTAS_API_KEY como contraseña)." >&2; exit 1; }; \
	else \
	  echo "Definí GOCUOTAS_JWT, o bien GOCUOTAS_EMAIL y GOCUOTAS_API_KEY (esta última es la contraseña de authenticate)." >&2; exit 1; \
	fi; \
	if [ -n "$$GOCUOTAS_DELIVERED_START" ] && [ -n "$$GOCUOTAS_DELIVERED_END" ]; then \
	  START="$$GOCUOTAS_DELIVERED_START"; END="$$GOCUOTAS_DELIVERED_END"; \
	else \
	  RANGE_RAW="$(GOCUOTAS_DELIVERED_RANGE)"; \
	  RANGE=$$(printf '%s' "$$RANGE_RAW" | tr '[:upper:]' '[:lower:]'); \
	  case "$$RANGE" in \
	    *d) DAYS=$${RANGE%d};; \
	    *) echo "GOCUOTAS_DELIVERED_RANGE debe ser relativo en días, ej. 30d o 7d (recibido: $$RANGE_RAW)" >&2; exit 1;; \
	  esac; \
	  case "$$DAYS" in ''|*[!0-9]*) echo "Número de días inválido en GOCUOTAS_DELIVERED_RANGE (usá solo dígitos + d, ej. 30d)" >&2; exit 1;; esac; \
	  END=$$(date +'%Y-%m-%d %H:%M'); \
	  START=$$(date -d "$$DAYS days ago" +'%Y-%m-%d %H:%M') || { echo "Se requiere GNU date(1) para calcular el rango (date -d). Exportá GOCUOTAS_DELIVERED_START y GOCUOTAS_DELIVERED_END a mano." >&2; exit 1; }; \
	fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" -G "$$base/api_redirect/v1/orders" \
		--data-urlencode "delivered_start=$$START" \
		--data-urlencode "delivered_end=$$END" \
		-H "Authorization: Bearer $$BEARER" \
		-H "Accept: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# Detalle en vivo: authenticate (opcional) → GET .../orders/{id}
# make buscar-orden GOCUOTAS_ORDER_ID=80001001
# o: export GOCUOTAS_ORDER_ID=80001001 && make buscar-orden
buscar-orden:
	@command -v jq >/dev/null 2>&1 || { echo "buscar-orden requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	ID="$(GOCUOTAS_ORDER_ID)"; \
	[ -n "$$ID" ] || ID="$${GOCUOTAS_ORDER_ID}"; \
	if [ -z "$$ID" ]; then echo "Definí GOCUOTAS_ORDER_ID (make buscar-orden GOCUOTAS_ORDER_ID=… o export en el shell)." >&2; exit 1; fi; \
	BEARER=""; \
	if [ -n "$$GOCUOTAS_JWT" ]; then \
	  BEARER="$$GOCUOTAS_JWT"; \
	elif [ -n "$$GOCUOTAS_EMAIL" ] && [ -n "$$GOCUOTAS_API_KEY" ]; then \
	  AUTH_JSON=$$(jq -n --arg e "$$GOCUOTAS_EMAIL" --arg p "$$GOCUOTAS_API_KEY" '{email: $$e, password: $$p}'); \
	  BEARER=$$(curl -sS "$$base/api_redirect/v1/authentication" \
	    -H "Content-Type: application/json" -H "Accept: application/json" \
	    -d "$$AUTH_JSON" | jq -er '(.token // .access_token)') \
	    || { echo "No se pudo obtener JWT (revisá GOCUOTAS_EMAIL y GOCUOTAS_API_KEY como contraseña)." >&2; exit 1; }; \
	else \
	  echo "Definí GOCUOTAS_JWT, o bien GOCUOTAS_EMAIL y GOCUOTAS_API_KEY (esta última es la contraseña de authenticate)." >&2; exit 1; \
	fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_redirect/v1/orders/$$ID" \
		-H "Authorization: Bearer $$BEARER" \
		-H "Accept: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# Reembolso en vivo: authenticate (opcional) → DELETE .../orders/{id}
# make reembolsar-orden GOCUOTAS_ORDER_ID=80001001
reembolsar-orden:
	@command -v jq >/dev/null 2>&1 || { echo "reembolsar-orden requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	ID="$(GOCUOTAS_ORDER_ID)"; \
	[ -n "$$ID" ] || ID="$${GOCUOTAS_ORDER_ID}"; \
	if [ -z "$$ID" ]; then echo "Definí GOCUOTAS_ORDER_ID (make reembolsar-orden GOCUOTAS_ORDER_ID=… o export en el shell)." >&2; exit 1; fi; \
	BEARER=""; \
	if [ -n "$$GOCUOTAS_JWT" ]; then \
	  BEARER="$$GOCUOTAS_JWT"; \
	elif [ -n "$$GOCUOTAS_EMAIL" ] && [ -n "$$GOCUOTAS_API_KEY" ]; then \
	  AUTH_JSON=$$(jq -n --arg e "$$GOCUOTAS_EMAIL" --arg p "$$GOCUOTAS_API_KEY" '{email: $$e, password: $$p}'); \
	  BEARER=$$(curl -sS "$$base/api_redirect/v1/authentication" \
	    -H "Content-Type: application/json" -H "Accept: application/json" \
	    -d "$$AUTH_JSON" | jq -er '(.token // .access_token)') \
	    || { echo "No se pudo obtener JWT (revisá GOCUOTAS_EMAIL y GOCUOTAS_API_KEY como contraseña)." >&2; exit 1; }; \
	else \
	  echo "Definí GOCUOTAS_JWT, o bien GOCUOTAS_EMAIL y GOCUOTAS_API_KEY (esta última es la contraseña de authenticate)." >&2; exit 1; \
	fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" -X DELETE "$$base/api_redirect/v1/orders/$$ID" \
		-H "Authorization: Bearer $$BEARER" \
		-H "Accept: application/json" \
		-H "Content-Type: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# API Client V1: GET .../api_client/v1/client (Bearer = API key de comercio)
# make informacion-comercio GOCUOTAS_COMMERCE_API_KEY=…  o export GOCUOTAS_COMMERCE_API_KEY
informacion-comercio:
	@command -v jq >/dev/null 2>&1 || { echo "informacion-comercio requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	KEY="$(GOCUOTAS_COMMERCE_API_KEY)"; \
	[ -n "$$KEY" ] || KEY="$${GOCUOTAS_COMMERCE_API_KEY}"; \
	if [ -z "$$KEY" ]; then echo "Definí GOCUOTAS_COMMERCE_API_KEY (make informacion-comercio GOCUOTAS_COMMERCE_API_KEY=… o export)." >&2; exit 1; fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_client/v1/client" \
		-H "Authorization: Bearer $$KEY" \
		-H "Accept: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# API Client V1: GET .../api_client/v1/expense_settlements (liquidaciones)
# make listar-liquidaciones GOCUOTAS_COMMERCE_API_KEY=…  o export GOCUOTAS_COMMERCE_API_KEY
listar-liquidaciones:
	@command -v jq >/dev/null 2>&1 || { echo "listar-liquidaciones requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	KEY="$(GOCUOTAS_COMMERCE_API_KEY)"; \
	[ -n "$$KEY" ] || KEY="$${GOCUOTAS_COMMERCE_API_KEY}"; \
	if [ -z "$$KEY" ]; then echo "Definí GOCUOTAS_COMMERCE_API_KEY (make listar-liquidaciones GOCUOTAS_COMMERCE_API_KEY=… o export)." >&2; exit 1; fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_client/v1/expense_settlements" \
		-H "Authorization: Bearer $$KEY" \
		-H "Accept: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# API Client V1: GET .../expense_settlements/{id}
# make informacion-liquidacion GOCUOTAS_LIQUIDACION_ID=9001001 GOCUOTAS_COMMERCE_API_KEY=…
informacion-liquidacion:
	@command -v jq >/dev/null 2>&1 || { echo "informacion-liquidacion requiere jq." >&2; exit 1; }; \
	base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	KEY="$(GOCUOTAS_COMMERCE_API_KEY)"; \
	[ -n "$$KEY" ] || KEY="$${GOCUOTAS_COMMERCE_API_KEY}"; \
	if [ -z "$$KEY" ]; then echo "Definí GOCUOTAS_COMMERCE_API_KEY." >&2; exit 1; fi; \
	ID="$(GOCUOTAS_LIQUIDACION_ID)"; \
	[ -n "$$ID" ] || ID="$${GOCUOTAS_LIQUIDACION_ID}"; \
	if [ -z "$$ID" ]; then echo "Definí GOCUOTAS_LIQUIDACION_ID (make informacion-liquidacion GOCUOTAS_LIQUIDACION_ID=… o export)." >&2; exit 1; fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_client/v1/expense_settlements/$$ID" \
		-H "Authorization: Bearer $$KEY" \
		-H "Accept: application/json"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		jq . <"$$tmp" 2>/dev/null || cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# API Client V1: GET .../expense_settlements_csvs (text/plain, CSV)
# make liquidaciones-texto-plano GOCUOTAS_COMMERCE_API_KEY=…
liquidaciones-texto-plano:
	@base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	KEY="$(GOCUOTAS_COMMERCE_API_KEY)"; \
	[ -n "$$KEY" ] || KEY="$${GOCUOTAS_COMMERCE_API_KEY}"; \
	if [ -z "$$KEY" ]; then echo "Definí GOCUOTAS_COMMERCE_API_KEY." >&2; exit 1; fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_client/v1/expense_settlements_csvs" \
		-H "Authorization: Bearer $$KEY" \
		-H "Accept: text/plain"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi

# API Client V1: GET .../expense_settlements_csvs/{id} (text/plain, CSV detalle)
# make informacion-liquidacion-texto-plano GOCUOTAS_LIQUIDACION_ID=9001001 GOCUOTAS_COMMERCE_API_KEY=…
informacion-liquidacion-texto-plano:
	@base="$(GOCUOTAS_API_URL)"; base=$${base%/}; \
	KEY="$(GOCUOTAS_COMMERCE_API_KEY)"; \
	[ -n "$$KEY" ] || KEY="$${GOCUOTAS_COMMERCE_API_KEY}"; \
	if [ -z "$$KEY" ]; then echo "Definí GOCUOTAS_COMMERCE_API_KEY." >&2; exit 1; fi; \
	ID="$(GOCUOTAS_LIQUIDACION_ID)"; \
	[ -n "$$ID" ] || ID="$${GOCUOTAS_LIQUIDACION_ID}"; \
	if [ -z "$$ID" ]; then echo "Definí GOCUOTAS_LIQUIDACION_ID." >&2; exit 1; fi; \
	tmp=$$(mktemp); \
	http=$$(curl -sS -o "$$tmp" -w "%{http_code}" "$$base/api_client/v1/expense_settlements_csvs/$$ID" \
		-H "Authorization: Bearer $$KEY" \
		-H "Accept: text/plain"); \
	if [ "$$http" -ge 200 ] && [ "$$http" -lt 300 ]; then \
		cat "$$tmp"; \
		rm -f "$$tmp"; echo; \
	else \
		echo "HTTP $$http" >&2; cat "$$tmp" >&2; rm -f "$$tmp"; exit 1; \
	fi
