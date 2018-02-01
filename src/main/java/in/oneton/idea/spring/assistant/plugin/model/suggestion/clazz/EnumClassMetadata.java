package in.oneton.idea.spring.assistant.plugin.model.suggestion.clazz;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import gnu.trove.THashMap;
import in.oneton.idea.spring.assistant.plugin.completion.SuggestionDocumentationHelper;
import in.oneton.idea.spring.assistant.plugin.model.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.model.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.model.suggestion.SuggestionNodeType;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.intellij.codeInsight.documentation.DocumentationManager.createHyperlink;
import static com.intellij.psi.PsiModifier.STATIC;
import static in.oneton.idea.spring.assistant.plugin.model.suggestion.SuggestionNode.sanitise;
import static in.oneton.idea.spring.assistant.plugin.model.suggestion.SuggestionNodeType.ENUM;
import static in.oneton.idea.spring.assistant.plugin.util.GenericUtil.dotDelimitedOriginalNames;
import static in.oneton.idea.spring.assistant.plugin.util.PsiCustomUtil.computeDocumentation;
import static in.oneton.idea.spring.assistant.plugin.util.PsiCustomUtil.getReferredPsiType;
import static in.oneton.idea.spring.assistant.plugin.util.PsiCustomUtil.toClassFqn;
import static in.oneton.idea.spring.assistant.plugin.util.PsiCustomUtil.toClassNonQualifiedName;
import static in.oneton.idea.spring.assistant.plugin.util.PsiCustomUtil.toValidPsiClass;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

public class EnumClassMetadata extends ClassMetadata {

  @NotNull
  private final PsiClassType type;

  @Nullable
  private Map<String, PsiField> childLookup;
  @Nullable
  private Trie<String, PsiField> childrenTrie;

  EnumClassMetadata(@NotNull PsiClassType type) {
    this.type = type;
    PsiClass enumClass = requireNonNull(toValidPsiClass(type));
    assert enumClass.isEnum();
  }

  @Override
  protected void init(Module module) {
    PsiClass psiClass = toValidPsiClass(type);
    init(psiClass);
  }

  @Nullable
  @Override
  protected SuggestionDocumentationHelper doFindDirectChild(Module module, String pathSegment) {
    if (childLookup != null && childLookup.containsKey(pathSegment)) {
      return new EnumKeySuggestionDocumentationHelper(childLookup.get(pathSegment));
    }
    return null;
  }

  @Override
  protected Collection<? extends SuggestionDocumentationHelper> doFindDirectChildrenForQueryPrefix(
      Module module, String querySegmentPrefix) {
    if (childrenTrie != null) {
      SortedMap<String, PsiField> prefixMap = childrenTrie.prefixMap(querySegmentPrefix);
      if (prefixMap != null && prefixMap.size() != 0) {
        return prefixMap.values().stream().map(EnumKeySuggestionDocumentationHelper::new)
            .collect(toList());
      }
    }
    return null;
  }

  @Nullable
  @Override
  protected List<SuggestionNode> doFindDeepestSuggestionNode(Module module,
      List<SuggestionNode> matchesRootTillParentNode, String[] pathSegments,
      int pathSegmentStartIndex) {
    throw new IllegalAccessError(
        "Should not be called. To use as a map key call findDirectChild(..) instead");
  }

  @Nullable
  @Override
  protected SortedSet<Suggestion> doFindKeySuggestionsForQueryPrefix(Module module,
      @Nullable String ancestralKeysDotDelimited, List<SuggestionNode> matchesRootTillParentNode,
      String[] querySegmentPrefixes, int querySegmentPrefixStartIndex) {
    throw new IllegalAccessError(
        "Should not be called. To use as a map key call findDirectChild(..) instead");
  }

  @Override
  protected SortedSet<Suggestion> doFindValueSuggestionsForPrefix(Module module,
      List<SuggestionNode> matchesRootTillMe, String prefix) {
    if (childrenTrie != null) {
      SortedMap<String, PsiField> prefixMap = childrenTrie.prefixMap(prefix);
      if (prefixMap != null && prefixMap.size() != 0) {
        return prefixMap.values().stream()
            .map(psiField -> newSuggestion(module, null, matchesRootTillMe, false, psiField))
            .collect(toCollection(TreeSet::new));
      }
    }
    return null;
  }

  @Nullable
  @Override
  protected String doGetDocumentationForValue(Module module, String nodeNavigationPathDotDelimited,
      String value) {
    if (childLookup != null) {
      // Format for the documentation is as follows
      /*
       * <p><b>a.b.c</b> ({@link com.acme.Generic}<{@link com.acme.Class1}, {@link com.acme.Class2}>)</p>
       * <p>Long description</p>
       * or of this type
       * <p><b>Type</b> {@link com.acme.Array}[]</p>
       * <p><b>Declared at</b>{@link com.acme.GenericRemovedClass#method}></p> <-- only for groups with method info
       */
      StringBuilder builder =
          new StringBuilder().append("<b>").append(nodeNavigationPathDotDelimited).append("</b>");

      String classFqn = toClassFqn(type);
      if (classFqn != null) {
        StringBuilder linkBuilder = new StringBuilder();
        createHyperlink(linkBuilder, classFqn, classFqn, false);
        builder.append(" (").append(linkBuilder.toString()).append(")");
      }

      PsiField psiField = childLookup.get(value);
      builder.append("<p>").append(requireNonNull(psiField.getName())).append("</p>");

      String documentation = computeDocumentation(psiField);
      if (documentation != null) {
        builder.append("<p>").append(documentation).append("</p>");
      }
      return builder.toString();
    }
    return null;
  }

  @Override
  public boolean isLeaf(Module module) {
    return true;
  }

  //  @Override
  //  public void refreshMetadata(Module module) {
  //    PsiClass psiClass = toValidPsiClass(type);
  //    if (psiClass != null) {
  //      if (childLookup == null && childrenTrie == null) {
  //        init(psiClass);
  //      }
  //    } else {
  //      if (childLookup != null && childrenTrie != null) {
  //        childLookup = null;
  //        childrenTrie = null;
  //      }
  //    }
  //  }

  @NotNull
  @Override
  public SuggestionNodeType getSuggestionNodeType() {
    return ENUM;
  }

  @Nullable
  @Override
  public PsiType getPsiType() {
    return type;
  }

  private void init(@Nullable PsiClass psiClass) {
    if (psiClass != null) {
      PsiField[] fields = psiClass.getFields();
      List<PsiField> acceptableFields = new ArrayList<>();
      for (PsiField field : fields) {
        if (field != null && !field.hasModifierProperty(STATIC)) {
          acceptableFields.add(field);
        }
      }
      if (acceptableFields.size() != 0) {
        childLookup = new THashMap<>();
        childrenTrie = new PatriciaTrie<>();
        acceptableFields.forEach(field -> {
          childLookup.put(sanitise(requireNonNull(field.getName())), field);
          childrenTrie.put(sanitise(field.getName()), field);
        });
      }
    }
  }

  private Suggestion newSuggestion(Module module, String ancestralKeysDotDelimited,
      List<SuggestionNode> matchesRootTillMe, boolean forKey, @NotNull PsiField value) {
    return Suggestion.builder().ancestralKeysDotDelimited(ancestralKeysDotDelimited).pathOrValue(
        forKey ?
            dotDelimitedOriginalNames(module, matchesRootTillMe) :
            requireNonNull(value.getName())).matchesTopFirst(matchesRootTillMe)
        .shortType(toClassNonQualifiedName(type)).icon(ENUM.getIcon()).build();
  }


  class EnumKeySuggestionDocumentationHelper implements SuggestionDocumentationHelper {
    private final PsiField field;

    EnumKeySuggestionDocumentationHelper(PsiField field) {
      this.field = field;
    }

    @Nullable
    @Override
    public String getOriginalName(Module module) {
      return field.getName();
    }

    @NotNull
    @Override
    public Suggestion buildSuggestion(Module module, String ancestralKeysDotDelimited,
        List<SuggestionNode> matchesRootTillMe) {
      return newSuggestion(module, ancestralKeysDotDelimited, matchesRootTillMe, true, field);
    }

    @Override
    public boolean supportsDocumentation() {
      return true;
    }

    @NotNull
    @Override
    public String getDocumentationForKey(Module module, String nodeNavigationPathDotDelimited) {
      /*
       * <p><b>a.b.c</b> ({@link com.acme.Generic}<{@link com.acme.Class1}, {@link com.acme.Class2}>)</p>
       * <p>Long description</p>
       */
      StringBuilder builder =
          new StringBuilder().append("<b>").append(nodeNavigationPathDotDelimited).append("</b>");

      String classFqn = toClassFqn(getReferredPsiType(field));

      if (classFqn != null) {
        StringBuilder linkBuilder = new StringBuilder();
        createHyperlink(linkBuilder, classFqn, classFqn, false);
        builder.append(" (").append(linkBuilder.toString()).append(")");
      }

      String documentation = computeDocumentation(field);
      if (documentation != null) {
        builder.append("<p>").append(documentation).append("</p>");
      }
      return builder.toString();
    }
  }

}
