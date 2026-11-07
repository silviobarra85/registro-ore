package it.registroore;
import java.util.*;
/** Test delle nove categorie e della migrazione prudente delle voci legacy. */
public final class CategoryTaxonomyTest {
  private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  public static void main(String[] args){
    check(CategoryTaxonomy.RULES.size()==9,"Nove categorie");
    check(new HashSet<>(List.of(CategoryTaxonomy.names())).size()==9,"Categorie univoche");
    check(CategoryTaxonomy.rule("COMMISSIONI").suspension()==CategoryTaxonomy.Suspension.VERIFY,"Commissioni verificabili");
    check(CategoryTaxonomy.rule(CategoryTaxonomy.FRONT).suspension()==CategoryTaxonomy.Suspension.BLOCK,"Frontale bloccata");
    check(CategoryTaxonomy.rule(CategoryTaxonomy.FUNDED).suspension()==CategoryTaxonomy.Suspension.ALLOW,"Ricerca progetti consentita");
    check(CategoryTaxonomy.mapLegacy("Progetti FRA","PROGETTI").target().equals(CategoryTaxonomy.FUNDED),"Migrazione progetti");
    check(CategoryTaxonomy.mapLegacy("Commissioni Dottorato","DIDATTICA INTEGRATIVA").target().equals(CategoryTaxonomy.BOARDS),"Commissioni più specifiche della vecchia regola");
    check(CategoryTaxonomy.mapLegacy("Categoria legacy ignota","ALTRE ATTIVITÀ").requiresReview(),"Mappa incerta da verificare");
    System.out.println("OK: 9 categorie, sospensioni e migrazioni conservative");
  }
}
