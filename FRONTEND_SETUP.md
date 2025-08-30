# Guide de Mise en Place du Frontend

## Architecture Cible

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Next.js App   │◄──►│  Spring Boot    │◄──►│   PostgreSQL    │
│                 │    │      API        │    │                 │
│ - Pages/Routes  │    │ - REST Endpoints│    │ - Employees     │
│ - Components    │    │ - Business Logic│    │ - Shifts        │
│ - State Mgmt    │    │ - OR-Tools      │    │ - Schedules     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Étape 1 : Configuration Base de Données

### 1.1 PostgreSQL Local
```bash
# Installation (Windows)
choco install postgresql
# ou télécharger depuis https://www.postgresql.org/download/windows/

# Démarrer le service
net start postgresql-x64-14

# Créer la base
psql -U postgres
CREATE DATABASE schedule_db;
CREATE USER schedule_user WITH PASSWORD 'schedule_password';
GRANT ALL PRIVILEGES ON DATABASE schedule_db TO schedule_user;
\q
```

### 1.2 Configuration Spring Boot
Ajouter à `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/schedule_db
    username: schedule_user
    password: schedule_password
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  profiles:
    active: dev

---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
```

### 1.3 Dépendances Maven
Ajouter à `pom.xml`:
```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Boot Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- H2 pour tests -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

## Étape 2 : Entités JPA

### 2.1 Structure des entités
```
src/main/java/com/cricri/
├── entity/
│   ├── EmployeeEntity.java
│   ├── ShiftTypeEntity.java
│   ├── ScheduleEntity.java
│   └── ConstraintConfigEntity.java
├── dto/
│   ├── EmployeeDto.java
│   ├── ShiftDto.java
│   └── ScheduleRequestDto.java
└── repository/
    ├── EmployeeRepository.java
    └── ScheduleRepository.java
```

### 2.2 Exemple EmployeeEntity
```java
@Entity
@Table(name = "employees")
public class EmployeeEntity {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String nom;
    
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // constructeurs, getters, setters
}
```

## Étape 3 : API REST

### 3.1 Structure des contrôleurs
```
src/main/java/com/cricri/controller/
├── EmployeeController.java
├── ShiftController.java
├── ScheduleController.java
└── ConstraintController.java
```

### 3.2 Exemple ScheduleController
```java
@RestController
@RequestMapping("/api/schedules")
@CrossOrigin(origins = "http://localhost:3000")
public class ScheduleController {
    
    @PostMapping("/generate")
    public ResponseEntity<ScheduleResponseDto> generateSchedule(
            @Valid @RequestBody ScheduleRequestDto request) {
        // Utiliser votre ShiftScheduler existant
        // Sauvegarder le résultat en BDD
        // Retourner le planning généré
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponseDto> getSchedule(@PathVariable String id) {
        // Récupérer depuis BDD
    }
}
```

## Étape 4 : Frontend Next.js

### 4.1 Initialisation
```bash
# Dans le dossier racine du projet
npx create-next-app@latest frontend --typescript --tailwind --eslint --app

cd frontend
npm install @tanstack/react-query axios @mui/material @mui/icons-material
npm install -D @types/node
```

### 4.2 Structure recommandée
```
frontend/
├── src/
│   ├── app/
│   │   ├── api/           # API routes Next.js (optionnel)
│   │   ├── schedule/      # Pages planning
│   │   ├── employees/     # Gestion employés
│   │   └── layout.tsx     # Layout global
│   ├── components/
│   │   ├── ui/            # Composants UI réutilisables
│   │   ├── schedule/      # Composants spécifiques au planning
│   │   └── forms/         # Formulaires
│   ├── lib/
│   │   ├── api.ts         # Client API
│   │   ├── types.ts       # Types TypeScript
│   │   └── utils.ts       # Utilitaires
│   └── hooks/
│       ├── useSchedule.ts # Hooks React Query
│       └── useEmployees.ts
├── public/
└── next.config.js
```

### 4.3 Configuration API Client
```typescript
// src/lib/api.ts
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  timeout: 30000, // OR-Tools peut prendre du temps
});

export const scheduleApi = {
  generateSchedule: (request: ScheduleRequest) => 
    api.post<ScheduleResponse>('/schedules/generate', request),
  
  getSchedule: (id: string) => 
    api.get<ScheduleResponse>(`/schedules/${id}`),
};

export default api;
```

### 4.4 React Query Setup
```typescript
// src/app/layout.tsx
'use client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      retry: 1,
    },
  },
});

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      </body>
    </html>
  );
}
```

## Étape 5 : Composants Clés

### 5.1 Hook personnalisé
```typescript
// src/hooks/useSchedule.ts
import { useMutation, useQuery } from '@tanstack/react-query';
import { scheduleApi } from '@/lib/api';

export const useGenerateSchedule = () => {
  return useMutation({
    mutationFn: scheduleApi.generateSchedule,
    onSuccess: (data) => {
      console.log('Planning généré:', data.data);
    },
    onError: (error) => {
      console.error('Erreur génération:', error);
    },
  });
};
```

### 5.2 Composant Planning Grid
```typescript
// src/components/schedule/ScheduleGrid.tsx
interface ScheduleGridProps {
  schedule: ScheduleResponse;
  employees: Employee[];
  shifts: Shift[];
}

export function ScheduleGrid({ schedule, employees, shifts }: ScheduleGridProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-collapse border">
        {/* Header avec les jours */}
        {/* Lignes avec employés et assignments */}
      </table>
    </div>
  );
}
```

## Étape 6 : Déploiement et Bonnes Pratiques

### 6.1 Variables d'environnement
```bash
# Backend (.env)
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://localhost:5432/schedule_db

# Frontend (.env.local)
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

### 6.2 Scripts NPM recommandés
```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "type-check": "tsc --noEmit"
  }
}
```

### 6.3 Validation et Error Handling
- **Backend** : Utiliser `@Valid` et `@ExceptionHandler`
- **Frontend** : React Error Boundaries et toast notifications
- **API** : Codes HTTP appropriés (200, 400, 500)

## Commandes de Démarrage

```bash
# Terminal 1 - Backend
./mvnw spring-boot:run

# Terminal 2 - Frontend
cd frontend
npm run dev
```

## Prochaines Étapes

1. Créer les entités JPA
2. Implémenter l'API REST 
3. Configurer Next.js
4. Développer les composants UI
5. Tester l'intégration

---

**Note**: Ce guide suit les conventions de votre projet (français, structure modulaire, TDD).