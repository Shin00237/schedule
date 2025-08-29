● Objectif Unifié pour les Contraintes SOFT - Spécifications d'Implémentation

  Contexte du Problème

  Situation actuelle (CASSÉE)

  - MaximizeWorkingHoursConstraint appelle context.getModel().maximize()
  - Les autres contraintes SOFT créent des violationVar avec des TODOs non
  implémentés
  - CONFLIT TECHNIQUE : OR-Tools CP-SAT ne peut avoir qu'UN seul objectif
  global
  - Résultat : Les contraintes SOFT autres que MaximizeWorkingHours ne
  fonctionnent pas

  Objectif cible

  Créer un système d'objectif unifié où toutes les contraintes SOFT
  contribuent à une seule expression d'objectif pondérée.

  Architecture de la Solution

  1. Nouvelle enum ObjectiveWeight (approche pragmatique)

  Créer src/main/java/com/cricri/constraints/enums/ObjectiveWeight.java :

  public enum ObjectiveWeight {
      // Pour maximiser (poids positifs)
      MAXIMIZE_CRITICAL(10000),     // MaximizeWorkingHours - objectif principal
      MAXIMIZE_HIGH(1000),          // Objectifs secondaires importants
      MAXIMIZE_MEDIUM(100),         // Objectifs secondaires moyens
      MAXIMIZE_LOW(10),             // Objectifs mineurs
      
      // Pour minimiser (poids négatifs) - APPROCHE SIMPLE
      MINIMIZE_CRITICAL(-10000),    // Violations critiques (sécurité)
      MINIMIZE_HIGH(-1000),         // Violations importantes (légal)
      MINIMIZE_MEDIUM(-100),        // Violations moyennes (confort)
      MINIMIZE_LOW(-10),            // Violations mineures
      
      DISABLED(0);                  // Pas d'objectif

      private final long weight;

      ObjectiveWeight(long weight) {
          this.weight = weight;
      }

      public long getWeight() {
          return weight;
      }
  }

  JUSTIFICATION DU CHOIX :
  Après analyse, l'approche "tout positif avec variables inverses" était de 
  l'over-engineering. Les poids négatifs sont :
  - Fonctionnellement corrects avec OR-Tools CP-SAT
  - Beaucoup plus simples à implémenter (pas de variables supplémentaires)
  - Plus performants (pas de contraintes addEquality supplémentaires)
  - Plus maintenables (pas de calcul complexe de maxViolation)

  2. Modification de SchedulingContext

  Ajouter dans SchedulingContext.java :

  public class SchedulingContext {
      // NOUVEAU : Accumulateur d'objectif global
      private LinearExprBuilder globalObjective = LinearExpr.newBuilder();
      private boolean objectiveFinalized = false;

      /**
       * Ajoute un terme pondéré à l'objectif global.
       * Doit être appelé avant finalizeObjective().
       */
      public void addObjectiveTerm(IntVar variable, long weight) {
          if (objectiveFinalized) {
              throw new IllegalStateException("Objectif déjà finalisé -
  impossible d'ajouter des termes");
          }
          globalObjective.addTerm(variable, weight);
      }

      /**
       * Finalise et applique l'objectif global au modèle OR-Tools.
       * Ne peut être appelé qu'une seule fois.
       */
      public void finalizeObjective() {
          if (!objectiveFinalized &&
  !globalObjective.build().equals(LinearExpr.constant(0))) {
              model.maximize(globalObjective.build());
              objectiveFinalized = true;
          }
      }

      public boolean isObjectiveFinalized() {
          return objectiveFinalized;
      }
  }

  3. Modification de ModularShiftScheduler.buildModel()

  Modifier la méthode pour implémenter le processus en deux phases :

  public void buildModel() {
      System.out.println("\n=== Construction du modèle modulaire ===");

      // PHASE 1: Appliquer toutes les contraintes (HARD et SOFT)
      // Les SOFT ajoutent leurs termes à l'objectif global
      constraints.stream()
          .sorted(Comparator.comparingInt(constraint ->
  constraint.getPriority().getValue()))
          .forEach(constraint -> {
              if (constraint.validate(context)) {
                  if (constraint.getNature() == ConstraintNature.HARD) {
                      constraint.applyHardConstraint(context);
                  } else {
                      constraint.applySoftConstraint(context); // Ajoute à
  l'objectif global
                  }
                  System.out.println("✓ Appliqué: " + constraint.getName());
              } else {
                  System.out.println("✗ Ignoré: " + constraint.getName() + "
  (validation échouée)");
              }
          });

      // PHASE 2: Finaliser l'objectif global unique
      context.finalizeObjective();

      System.out.println("Modèle construit avec " + constraints.size() + "
  contraintes");
  }

  4. ConstraintConfig reste inchangé

  ConstraintConfig.java garde sa structure actuelle - PAS de modification :

  public record ConstraintConfig(
      ConstraintType type,
      ConstraintNature nature,
      ConstraintPriority priority,
      Map<String, Object> parameters
  ) {
      // API existante inchangée
      public static ConstraintConfig of(...) { ... }
  }

  5. Les contraintes gèrent leurs propres poids

  Chaque contrainte SOFT implémente sa propre logique de poids selon sa nature.

  6. Refactorisation des contraintes SOFT

  6.1. MaximizeWorkingHoursConstraint

  AVANT (PROBLÉMATIQUE) :
  @Override
  public void applySoftConstraint(SchedulingContext context) {
      // ... construction de l'objectif
      context.getModel().maximize(objective);  // ← PROBLÈME : Un seul
  objectif possible
  }

  APRÈS (CORRECT) :
  @Override
  public void applySoftConstraint(SchedulingContext context) {
      context.ensureVariablesInitialized();

      ObjectiveWeight weight = getObjectiveWeight(); // À implémenter

      // Objectif principal : maximiser les heures réelles travaillées
      for (int e = 0; e < context.getEmployeeCount(); e++) {
          for (int s = 0; s < context.getShiftCount(); s++) {
              context.addObjectiveTerm(context.getActualHours()[e][s],
  weight.getWeight());
          }
      }

      // Objectif secondaire : favoriser les jours de semaine
      for (int s = 0; s < context.getShiftCount(); s++) {
          Shift shift = context.getShifts().get(s);
          if (!isWeekend(shift)) {
              for (int e = 0; e < context.getEmployeeCount(); e++) {
                  // Bonus pondéré pour les jours de semaine
                  long bonusWeight = (weight.getWeight() * weekdayMultiplier)
   / 10;
                  context.addObjectiveTerm(context.getActualHours()[e][s],
  bonusWeight);
              }
          }
      }
  }

  6.2. Autres contraintes SOFT (violations) - APPROCHE SIMPLE

  Pour TOUTES les autres contraintes avec des TODOs, remplacer :

  AVANT :
  // TODO: Ajouter cette violation à un objectif global de minimisation

  APRÈS :
  // Ajouter directement la violation avec un poids négatif (minimisation)
  ObjectiveWeight weight = getObjectiveWeight(); // Retourne MINIMIZE_xxx
  context.addObjectiveTerm(violationVar, weight.getWeight()); // Poids négatif = minimise

  EXEMPLE CONCRET :
  // Dans MinimumRestConstraint.applySoftConstraint()
  var violationVar = context.getModel().newBoolVar("rest_conflict_e" + e + "_s" + s1 + "_s" + s2);
  // ... logique de la violation ...
  
  ObjectiveWeight weight = getObjectiveWeight(); // Retourne MINIMIZE_CRITICAL (-10000)
  context.addObjectiveTerm(violationVar, weight.getWeight()); // -10000 = forte pénalité

  6.3. Chaque contrainte définit son propre poids

  Chaque classe de contrainte SOFT implémente sa logique de poids :

  public class MaximizeWorkingHoursConstraint implements Constraint {
      
      private ObjectiveWeight getObjectiveWeight() {
          // MaximizeWorkingHours = objectif critique (MAXIMISER les heures)
          return this.nature == ConstraintNature.SOFT 
              ? ObjectiveWeight.MAXIMIZE_CRITICAL 
              : ObjectiveWeight.DISABLED;
      }
  }

  public class MinimumRestDaysConstraint implements Constraint {
      
      private ObjectiveWeight getObjectiveWeight() {
          // RestDays = violation de confort (MINIMISER les violations)
          return this.nature == ConstraintNature.SOFT 
              ? ObjectiveWeight.MINIMIZE_MEDIUM 
              : ObjectiveWeight.DISABLED;
      }
  }

  public class MinimumRestConstraint implements Constraint {
      
      private ObjectiveWeight getObjectiveWeight() {
          // Rest = violation critique de sécurité (MINIMISER fortement)
          return this.nature == ConstraintNature.SOFT 
              ? ObjectiveWeight.MINIMIZE_CRITICAL 
              : ObjectiveWeight.DISABLED;
      }
  }

  // Autres contraintes : même principe avec leur logique spécifique

  Exemple d'utilisation finale

  // Dans Main.java ou ailleurs - API inchangée !
  List<ConstraintConfig> constraintConfigs = Arrays.asList(
      // Contraintes HARD (pas d'objectif)
      ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE,
          ConstraintNature.HARD, ConstraintPriority.FUNDAMENTAL),

      // Contraintes SOFT - les poids sont gérés automatiquement par chaque contrainte
      ConstraintConfig.of(
          ConstraintType.MAXIMIZE_WORKING_HOURS,
          ConstraintNature.SOFT,
          ConstraintPriority.OPTIMIZATION,
          "weekdayMultiplier", 2
      ),

      ConstraintConfig.of(
          ConstraintType.MINIMUM_REST_DAYS,
          ConstraintNature.SOFT,
          ConstraintPriority.COMFORT,
          "minRestDaysPerWeek", 2
      )
  );

  Points critiques d'implémentation

  1. Ordre d'exécution strict

  - PHASE 1 : Toutes les contraintes s'appliquent et contribuent à l'objectif
  - PHASE 2 : context.finalizeObjective() ne doit être appelé qu'UNE FOIS à
  la fin

  2. Gestion des erreurs

  - addObjectiveTerm() après finalizeObjective() → Exception
  - Protéger contre les ajouts multiples d'objectifs

  3. Simplicité et compatibilité

  - ConstraintConfig reste inchangé - aucun impact sur l'API utilisateur
  - Chaque contrainte gère sa propre logique de poids (séparation des responsabilités)
  - Les contraintes HARD ignorent les objectifs (getObjectiveWeight() retourne DISABLED)

  4. Scaling des poids et approche pragmatique

  - Objectifs directs : poids positifs (MAXIMIZE_xxx = +valeur)
  - Violations : poids négatifs (MINIMIZE_xxx = -valeur)
  - Calibrage : CRITICAL (±10000) > HIGH (±1000) > MEDIUM (±100) > LOW (±10)
  - Approche simple et performante : pas de variables ou contraintes supplémentaires

  Tests à implémenter

  1. Test unitaire : Vérifier qu'un seul model.maximize() est appelé
  2. Test d'intégration : Vérifier que les contraintes SOFT fonctionnent
  ensemble
  3. Test de régression : S'assurer que le comportement de
  MaximizeWorkingHours est préservé
  4. Test des poids négatifs : Vérifier que les violations sont effectivement 
  pénalisées (objectif global diminue quand les violations augmentent)

  Résultat attendu

  Après implémentation, toutes les contraintes SOFT contribueront à un
  objectif global unifié pragmatique, permettant à OR-Tools d'optimiser :
  - Les objectifs positifs (heures travaillées) avec poids +
  - Les violations (à minimiser) avec poids -
  - Performance optimale sans complexité technique supplémentaire
  - Code simple, maintenable et extensible
