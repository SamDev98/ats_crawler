# Carrega variáveis do arquivo .env se ele existir
ifneq ("$(wildcard .env)","")
    include .env
    export $(shell sed 's/=.*//' .env)
endif

# Job Scanner - Makefile
# Atalhos para comandos comuns

.PHONY: help build run run-dry test clean docker docker-run docker-stop

# Cores para output
CYAN := \033[36m
GREEN := \033[32m
YELLOW := \033[33m
RESET := \033[0m

help: ## Mostra esta ajuda
	@echo "$(CYAN)Job Scanner - Comandos disponíveis:$(RESET)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-15s$(RESET) %s\n", $$1, $$2}'
	@echo ""

build: ## Compila o projeto
	@echo "$(YELLOW)Compilando...$(RESET)"
	./mvnw package -DskipTests -q
	@echo "$(GREEN)Build completo!$(RESET)"

run: build ## Executa o scanner (produção)
	@echo "$(YELLOW)Executando Job Scanner...$(RESET)"
	java -jar target/*.jar

run-dry: build ## Executa em modo dry-run (sem enviar email)
	@echo "$(YELLOW)Executando em modo DRY-RUN...$(RESET)"
	DRY_RUN=true java -jar target/*.jar

test: ## Executa os testes
	./mvnw test

clean: ## Limpa arquivos de build
	./mvnw clean -q
	@echo "$(GREEN)Limpeza concluída!$(RESET)"

reset-db: ## Apaga o histórico de vagas (zerar banco)
	@echo "$(YELLOW)Zerando histórico de vagas...$(RESET)"
	rm -f data/jobs.db*
	@echo "$(GREEN)Banco de dados reiniciado!$(RESET)"

fresh-dry: reset-db run-dry ## Zera o banco e executa em modo dry-run

compile: ## Apenas compila (sem empacotar)
	./mvnw compile -q
	@echo "$(GREEN)Compilação OK!$(RESET)"

# Docker commands
docker: ## Build da imagem Docker
	@echo "$(YELLOW)Construindo imagem Docker...$(RESET)"
	docker build -t job-scanner .
	@echo "$(GREEN)Imagem construída!$(RESET)"

docker-run: ## Executa via Docker
	docker compose up

docker-run-dry: ## Executa via Docker em dry-run
	DRY_RUN=true docker compose up

docker-stop: ## Para containers Docker
	docker compose down

docker-logs: ## Mostra logs do container
	docker compose logs -f job-scanner

# Monitoring (Prometheus + Grafana)
monitor-up: ## Inicia stack de monitoramento
	docker compose --profile monitoring up -d
	@echo "$(GREEN)Prometheus: http://localhost:9090$(RESET)"
	@echo "$(GREEN)Grafana: http://localhost:3000 (admin/admin)$(RESET)"

monitor-down: ## Para stack de monitoramento
	docker compose --profile monitoring down

# Database
db-reset: ## Reseta o banco SQLite
	rm -f data/jobs.db
	@echo "$(GREEN)Banco resetado!$(RESET)"

db-stats: ## Mostra estatísticas do banco
	@echo "$(CYAN)Jobs enviados:$(RESET)"
	@sqlite3 data/jobs.db "SELECT COUNT(*) as total FROM sent_jobs;" 2>/dev/null || echo "Banco não existe"
	@sqlite3 data/jobs.db "SELECT source, COUNT(*) as count FROM sent_jobs GROUP BY source;" 2>/dev/null || true
