package in.oneton.idea.spring.assistant.plugin.model.suggestion.metadata;

import com.intellij.openapi.module.Module;
import in.oneton.idea.spring.assistant.plugin.model.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.model.suggestion.SuggestionNode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;

import static java.util.stream.Collectors.joining;

public abstract class MetadataSuggestionNode implements SuggestionNode {

  //  static MetadataSuggestionNode NULL_NODE = null;

  /**
   * If {@code matchAllSegments} is true, all {@code pathSegments} starting from {@code pathSegmentStartIndex} will be attempted to be matched. If a result is found, it will be returned. Else null
   * Else, method should attempt to match as deep as it can & return that match
   *
   * @param pathSegments          path segments to match against
   * @param pathSegmentStartIndex index within {@code pathSegments} to start match from
   * @param matchAllSegments      should all {@code pathSegments} be matched or not
   * @return leaf suggestion node that matches path segments starting with {@code pathSegmentStartIndex} or null otherwise
   */
  @Contract("_, _, true -> null; _, _, false -> !null")
  public abstract MetadataSuggestionNode findDeepestMetadataNode(String[] pathSegments,
      int pathSegmentStartIndex, boolean matchAllSegments);

  @Override
  public SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(Module module,
      String ancestralKeysDotDelimited, List<SuggestionNode> matchesRootTillMe,
      String[] querySegmentPrefixes, int querySegmentPrefixStartIndex) {
    return findKeySuggestionsForQueryPrefix(module, ancestralKeysDotDelimited, matchesRootTillMe,
        querySegmentPrefixes, querySegmentPrefixStartIndex, true);
  }

  /**
   * @param module                       module
   * @param ancestralKeysDotDelimited    all ancestral keys dot delimited, required for showing full path in documentation
   * @param matchesRootTillMe            path from root till current node
   * @param querySegmentPrefixes         the search text parts split based on period delimiter
   * @param querySegmentPrefixStartIndex current index in the `querySegmentPrefixes` to start search from
   * @param navigateDeepIfNoMatches      whether search should proceed further down if the search cannot find results at this level
   * @return Suggestions matching the given querySegmentPrefixes criteria from within the children
   */
  @Nullable
  protected abstract SortedSet<Suggestion> findKeySuggestionsForQueryPrefix(Module module,
      @Nullable String ancestralKeysDotDelimited, List<SuggestionNode> matchesRootTillMe,
      String[] querySegmentPrefixes, int querySegmentPrefixStartIndex,
      boolean navigateDeepIfNoMatches);


  public void addRefCascadeTillRoot(String containerPath) {
    MetadataSuggestionNode node = this;
    do {
      if (node.getBelongsTo().contains(containerPath)) {
        break;
      }
      node.getBelongsTo().add(containerPath);
      node = node.getParent();
    } while (node != null && !node.isRoot());
  }

  public abstract Set<String> getBelongsTo();

  /**
   * @param containerPath Represents path to the metadata file container
   * @return true if no children left & this item does not belong to any other source
   */
  public abstract boolean removeRefCascadeDown(String containerPath);

  /**
   * During reindexing lets make sure that we refresh references to proxies so that subsequent searches would be faster
   *
   * @param module idea module
   */
  public abstract void refreshClassProxy(Module module);

  protected abstract String getName();

  @NotNull
  public abstract String getOriginalName(Module module);

  @Nullable
  protected abstract MetadataNonPropertySuggestionNode getParent();

  protected abstract boolean isRoot();

  /**
   * @return whether the node expects any children or not
   */
  public abstract boolean isGroup();

  /**
   * @return whether the node expects any children or not
   */
  public abstract boolean isProperty();

  protected abstract boolean hasOnlyOneChild(Module module);

  public int numOfHopesToRoot() {
    int hopCount = 0;
    MetadataSuggestionNode current = getParent();
    while (current != null) {
      hopCount++;
      current = current.getParent();
    }
    return hopCount;
  }

  public String getPathFromRoot(Module module) {
    Stack<String> leafTillRoot = new Stack<>();
    MetadataSuggestionNode current = this;
    do {
      leafTillRoot.push(current.getOriginalName(module));
      current = current.getParent();
    } while (current != null);
    return leafTillRoot.stream().collect(joining("."));
  }

  @Override
  public boolean supportsDocumentation() {
    return isGroup() || isProperty();
  }

  @Nullable
  @Override
  public String getNameForDocumentation(Module module) {
    return getSuggestionNodeType(module).representsArrayOrCollection() ?
        getOriginalName(module) + "[]" :
        getOriginalName(module);
  }

}
