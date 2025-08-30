# Analyse des Logs OR-Tools CP-SAT

## Vue d'ensemble

Cette analyse compare les logs détaillés d'OR-Tools entre un cas de **succès (OPTIMAL)** et un cas d'**échec (INFEASIBLE)** pour comprendre les informations disponibles pour le diagnostic utilisateur.

## Comparaison des Cas

### ✅ Cas OPTIMAL (successLog.txt)

**Configuration du modèle :**
```
Variables: 199 (#ints: 70 in objective)
- 105 Booleans in [0,1]
- 14 in [0,5] 
- 5 in [0,7]
- 70 in [0,480]
- 5 in [0,2147483647]

Contraintes: 6 configurées
Temps de résolution: 81ms
```

**Progression de recherche :**
- 11 solutions trouvées progressivement
- 390 branches explorées, 33 conflits
- Recherche complète avec multiple subsolvers
- Solution optimale atteinte via 'default_lp'

### ❌ Cas INFEASIBLE (failedLog.txt)

**Configuration du modèle :**
```
Variables: 234 (#ints: 70 in objective)  
- 140 Booleans in [0,1]
- Plus de contraintes linéaires (#kLinear3: 35 nouvelles)

Contraintes: 7 configurées (ShiftOverlap ajoutée)
Temps de résolution: 8ms
```

**Arrêt immédiat :**
```
INFEASIBLE: 'during probing'
Problem closed by presolve.
Branches: 0, Conflits: 0
```

## Analyse Détaillée des Logs d'Échec

### Phase de Presolve

**Simplifications SAT :**
```
[0s]         clauses:326  literals:750  vars:140
[0.0001961s] clauses:226  literals:515  vars:140  ← 100 clauses éliminées
[0.0002966s] clauses:219  literals:522  vars:133  ← 7 variables éliminées
```

**Point d'échec :**
```
[Probing] implications and bool_or (work_done=900)
INFEASIBLE: 'during probing'
```

### Détection de Symétries

**Structure identifiée :**
```
Graph for symmetry: 734 nodes, 1355 arcs
#generators: 4, average support size: 84
42 orbits with sizes: 5,5,5,5,5,5,5,5,5,5...
Found orbitope of size 42 x 5
```

**Interprétation :**
- **5 employés symétriques** (Alice, Bob, Charlie, David, Eva)
- **42 groupes** d'éléments équivalents 
- OR-Tools détecte automatiquement l'interchangeabilité des employés

### Presolve Summary - Règles Appliquées

**Simplifications massives :**
- `linear: simplified rhs` : **490 fois** → Simplification intensive des contraintes
- `linear2: contains a Boolean` : **540 fois** → Nombreuses contraintes mixtes
- `bool_or: implications` : **270 fois** → Tests d'implications logiques
- `linear: negative clause` : **235 fois** → Conversion en clauses SAT
- `linear: positive clause` : **91 fois**

**Contraintes spécifiques :**
- `TODO dual: only one blocking constraint?` : **35 fois** → Probablement les 35 shifts
- `variables: detect half reified value encoding` : **70 fois** → Variables liées détectées

**Diagnostic final :**
- `probing: simplified clauses` : **2 fois seulement** → Échec rapide lors du probing

## Informations Exploitables pour le Feedback Utilisateur

### Ce que les Logs Révèlent

**✅ Informations disponibles :**
1. **Type d'échec** : `during probing` = conflit logique booléen
2. **Moment de détection** : Presolve (avant recherche) = incompatibilité fondamentale
3. **Complexité** : Nombre de simplifications tentées
4. **Structure** : Symétries détectées, variables créées

**❌ Informations manquantes :**
1. **Quelle contrainte** pose problème exactement
2. **Pourquoi** les contraintes sont incompatibles
3. **Comment** résoudre le problème
4. **Quels paramètres** ajuster

### Patterns de Diagnostic Automatique

```java
// Diagnostic basé sur les patterns des logs
private String analyzeSolverFailure(CpSolver solver) {
    String responseStats = solver.responseStats();
    
    if (responseStats.contains("INFEASIBLE: 'during probing'")) {
        return "Conflit logique détecté entre contraintes booléennes. " +
               "Certaines règles s'excluent mutuellement.";
    }
    
    if (responseStats.contains("Problem closed by presolve")) {
        return "Contradictions détectées avant la recherche. " +
               "Vérifiez la compatibilité de vos contraintes.";
    }
    
    if (solver.numBranches() == 0 && solver.numConflicts() == 0) {
        return "Modèle fondamentalement impossible - " +
               "aucune exploration nécessaire.";
    }
    
    if (responseStats.contains("bool_or: implications") && 
        responseStats.contains("270 times")) {
        return "270 implications booléennes testées - " +
               "conflit dans les règles logiques.";
    }
    
    return "Aucune solution trouvée";
}
```

## Métriques Clés pour l'UX

### Indicateurs de Complexité
- **Nombre de variables** : Plus de variables = problème plus complexe
- **Temps de résolution** : < 10ms = détection rapide d'impossibilité
- **Nombre de branches** : 0 = pas d'exploration = problème fondamental
- **Règles de presolve** : > 1000 applications = simplifications massives tentées

### Suggestions d'Amélioration
1. **Feedback contextuel** : Utiliser les patterns des logs pour des messages spécifiques
2. **Diagnostic progressif** : Tenter la résolution sans certaines contraintes
3. **Validation pré-résolution** : Détecter les incompatibilités évidentes
4. **Mode debug** : Afficher les statistiques de presolve pour les utilisateurs avancés

## Conclusion

Les logs OR-Tools fournissent des informations précieuses sur **le type et le moment** de l'échec, mais restent insuffisants pour un **diagnostic précis** des causes. 

La valeur ajoutée de l'application réside dans l'interprétation intelligente de ces signaux pour fournir des suggestions concrètes à l'utilisateur.

**Recommandation** : Implémenter une couche de diagnostic qui analyse les patterns des logs OR-Tools et traduit les informations techniques en conseils utilisateur actionnables.