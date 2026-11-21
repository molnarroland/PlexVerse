package hu.molnarroland.plexverse

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import hu.molnarroland.plexverse.ui.preview.PartPreviews
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import hu.molnarroland.plexverse.feature.category.CategoryPreview
import hu.molnarroland.plexverse.feature.category.CollectionDrawerPreview
import hu.molnarroland.plexverse.feature.category.CollectionsCollapsedPreview
import hu.molnarroland.plexverse.feature.category.CollectionsGroupedPreview
import hu.molnarroland.plexverse.feature.category.OtherCategoryPreview
import hu.molnarroland.plexverse.feature.folder.FolderPreview
import hu.molnarroland.plexverse.feature.home.HomePreview
import hu.molnarroland.plexverse.feature.home.PrefetchPreview
import hu.molnarroland.plexverse.feature.kids.KidsGatePreview
import hu.molnarroland.plexverse.feature.kids.KidsHomePreview
import hu.molnarroland.plexverse.feature.kids.KidsSearchPreview
import hu.molnarroland.plexverse.feature.kids.KidsSettingsPreview
import hu.molnarroland.plexverse.feature.kids.SurpriseButtonPreview
import hu.molnarroland.plexverse.feature.search.SearchPreview
import hu.molnarroland.plexverse.feature.search.SearchResultsPreview
import hu.molnarroland.plexverse.feature.settings.SettingsAppearancePreview
import hu.molnarroland.plexverse.feature.settings.SettingsPreview
import hu.molnarroland.plexverse.feature.setup.ManageCategoriesPreview
import hu.molnarroland.plexverse.feature.setup.SetupCategoriesFirstRunPreview
import hu.molnarroland.plexverse.feature.setup.SetupCategoriesPreview
import hu.molnarroland.plexverse.feature.setup.SetupServersPreview
import hu.molnarroland.plexverse.feature.show.ShowPreview
import hu.molnarroland.plexverse.feature.start.ProfilePickerCrowdedPreview
import hu.molnarroland.plexverse.feature.start.ProfilePickerPreview
import hu.molnarroland.plexverse.feature.start.SignInFindingServersPreview
import hu.molnarroland.plexverse.feature.start.SignInPreview
import hu.molnarroland.plexverse.feature.start.SplashPreview
import hu.molnarroland.plexverse.ui.ContinueRowPreview
import hu.molnarroland.plexverse.ui.DialogsPreview
import hu.molnarroland.plexverse.ui.EntryPostersPreview
import hu.molnarroland.plexverse.ui.ServersPreview
import hu.molnarroland.plexverse.ui.TopMenuPreview
import hu.molnarroland.plexverse.ui.theme.MarkPreview

// Every preview in the app, rendered by `./gradlew updateDebugScreenshotTest` into
// src/screenshotTestDebug/reference, and checked against those pictures by `validateDebugScreenshotTest`.

@PreviewTest
@TvPreviews
@Composable
fun CategoryScreenshot() = CategoryPreview()

@PreviewTest
@PartPreviews
@Composable
fun ContinueRowScreenshot() = ContinueRowPreview()

@PreviewTest
@Preview(name = "Dialogs", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dialogs day", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun DialogsScreenshot() = DialogsPreview()

@PreviewTest
@Preview(name = "Posters", widthDp = 960, heightDp = 260, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun EntryPostersScreenshot() = EntryPostersPreview()

@PreviewTest
@TvPreviews
@Composable
fun FolderScreenshot() = FolderPreview()

@PreviewTest
@TvPreviews
@Composable
fun HomeScreenshot() = HomePreview()

@PreviewTest
@Preview(name = "Kids gate", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun KidsGateScreenshot() = KidsGatePreview()

@PreviewTest
@TvPreviews
@Composable
fun KidsHomeScreenshot() = KidsHomePreview()

@PreviewTest
@TvPreviews
@Composable
fun KidsSearchScreenshot() = KidsSearchPreview()

@PreviewTest
@TvPreviews
@Composable
fun KidsSettingsScreenshot() = KidsSettingsPreview()

@PreviewTest
@TvPreviews
@Composable
fun ManageCategoriesScreenshot() = ManageCategoriesPreview()

@PreviewTest
@Preview(name = "Mark", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Mark day", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun MarkScreenshot() = MarkPreview()

@PreviewTest
@TvPreviews
@Composable
fun OtherCategoryScreenshot() = OtherCategoryPreview()

@PreviewTest
@TvPreviews
@Composable
fun PrefetchScreenshot() = PrefetchPreview()

@PreviewTest
@TvPreviews
@Composable
fun ProfilePickerScreenshot() = ProfilePickerPreview()

@PreviewTest
@TvPreviews
@Composable
fun ProfilePickerCrowdedScreenshot() = ProfilePickerCrowdedPreview()

@PreviewTest
@TvPreviews
@Composable
fun SearchScreenshot() = SearchPreview()

@PreviewTest
@TvPreviews
@Composable
fun SearchResultsScreenshot() = SearchResultsPreview()

@PreviewTest
@Preview(name = "Servers", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Servers day", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun ServersScreenshot() = ServersPreview()

@PreviewTest
@TvPreviews
@Composable
fun SettingsAppearanceScreenshot() = SettingsAppearancePreview()

@PreviewTest
@TvPreviews
@Composable
fun SettingsScreenshot() = SettingsPreview()

@PreviewTest
@TvPreviews
@Composable
fun SetupCategoriesScreenshot() = SetupCategoriesPreview()

@PreviewTest
@TvPreviews
@Composable
fun SetupCategoriesFirstRunScreenshot() = SetupCategoriesFirstRunPreview()

@PreviewTest
@TvPreviews
@Composable
fun SetupServersScreenshot() = SetupServersPreview()

@PreviewTest
@TvPreviews
@Composable
fun ShowScreenshot() = ShowPreview()

@PreviewTest
@TvPreviews
@Composable
fun SignInFindingServersScreenshot() = SignInFindingServersPreview()

@PreviewTest
@TvPreviews
@Composable
fun SignInScreenshot() = SignInPreview()

@PreviewTest
@TvPreviews
@Composable
fun SplashScreenshot() = SplashPreview()

@PreviewTest
@Preview(name = "Surprise me!", widthDp = 400, heightDp = 200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Surprise me! day", widthDp = 400, heightDp = 200, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun SurpriseButtonScreenshot() = SurpriseButtonPreview()

@PreviewTest
@PartPreviews
@Composable
fun TopMenuScreenshot() = TopMenuPreview()

@PreviewTest
@TvPreviews
@Composable
fun CollectionsGroupedScreenshot() = CollectionsGroupedPreview()

@PreviewTest
@TvPreviews
@Composable
fun CollectionsCollapsedScreenshot() = CollectionsCollapsedPreview()

@PreviewTest
@TvPreviews
@Composable
fun CollectionDrawerScreenshot() = CollectionDrawerPreview()
