using CommunityToolkit.Maui.Views;
using System.Linq;
using System.Threading;
#if IOS
using UIKit;
using Microsoft.Maui.Platform;
#endif

namespace MauiSample9;

public partial class DialogsPage : ContentPage
{
	private CancellationTokenSource? _timerCts;

	public DialogsPage()
	{
		InitializeComponent();
		NativeBottomSheet.Closed += OnNativeBottomSheetClosed;
	}

	private async Task WaitForDelay()
	{
		if (double.TryParse(DelayEntry.Text, out double seconds) && seconds > 0)
		{
			await Task.Delay(TimeSpan.FromSeconds(seconds));
		}
	}

	private async Task AutoClose(Func<Task> closeAction)
	{
		if (double.TryParse(DelayEntry.Text, out double seconds) && seconds > 0)
		{
			await Task.Delay(TimeSpan.FromSeconds(seconds));
			await closeAction();
		}
	}

	// --- Alerts ---

	private async void OnSimpleAlertClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		// Note: DisplayAlert cannot be closed programmatically in MAUI.
		await DisplayAlert("Simple Alert", "This is a simple alert dialog. (Auto-close not supported for native Alerts)", "OK");
	}

	private async void OnAcceptCancelAlertClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		bool answer = await DisplayAlert("Question", "Do you want to proceed?", "Yes", "No");
		await DisplayAlert("Result", $"You chose: {(answer ? "Yes" : "No")}", "OK");
	}

	private async void OnPromptClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		string? result = await DisplayPromptAsync("Prompt", "Enter your name:", "OK", "Cancel", "Name...");
		if (result is not null)
			await DisplayAlert("Prompt Result", $"You entered: {result}", "OK");
	}

	// --- Bottom Sheets ---

	private async void OnActionSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		// Note: DisplayActionSheet cannot be closed programmatically in MAUI.
		string action = await DisplayActionSheet(
			"Action Sheet: Choose an option",
			"Cancel",
			"Delete",
			"Option A", "Option B", "Option C");
		Console.WriteLine($"Action Sheet selection: {action}");
	}

	private async void OnXamlOverlaySheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		OverlayDim.InputTransparent = false;
		OverlayDim.IsVisible = true;
		XamlOverlaySheet.InputTransparent = false;
		XamlOverlaySheet.IsVisible = true;
		XamlOverlaySheet.TranslationY = 400;
		_ = XamlOverlaySheet.TranslateTo(0, 0, 300, Easing.CubicOut);
		
		await AutoClose(DismissXamlOverlay);
	}

	private async void OnCenteredPopupCardClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		
		_timerCts?.Cancel();
		_timerCts = new CancellationTokenSource();
		var token = _timerCts.Token;

		PopupCardDim.InputTransparent = false;
		PopupCardDim.IsVisible = true;
		CenteredPopupCard.InputTransparent = false;
		CenteredPopupCard.IsVisible = true;
		CenteredPopupCard.Scale = 0.8;
		CenteredPopupCard.Opacity = 0;
		_ = Task.WhenAll(
			CenteredPopupCard.ScaleTo(1, 250, Easing.CubicOut),
			CenteredPopupCard.FadeTo(1, 250));

		_ = Task.Run(async () =>
		{
			if (!int.TryParse(DelayEntry.Text, out int seconds) || seconds <= 0)
			{
				seconds = 8; // Default if 0 or invalid
			}

			while (seconds >= 0 && !token.IsCancellationRequested)
			{
				MainThread.BeginInvokeOnMainThread(() =>
				{
					PopupTimerLabel.Text = $"{seconds / 60:D2}:{seconds % 60:D2}";
				});
				await Task.Delay(1000);
				seconds--;
			}
		}, token);

		await AutoClose(DismissPopupCard);
	}

	private async void OnPopupCardDimTapped(object? sender, TappedEventArgs e)
	{
		await DismissPopupCard();
	}

	private async void OnPopupCardCloseClicked(object? sender, EventArgs e)
	{
		await DismissPopupCard();
	}

	private async Task DismissPopupCard()
	{
		if (!CenteredPopupCard.IsVisible) return;
		
		_timerCts?.Cancel();
		_timerCts = null;

		await Task.WhenAll(
			CenteredPopupCard.ScaleTo(0.8, 200, Easing.CubicIn),
			CenteredPopupCard.FadeTo(0, 200));
		CenteredPopupCard.IsVisible = false;
		CenteredPopupCard.InputTransparent = true;
		PopupCardDim.IsVisible = false;
		PopupCardDim.InputTransparent = true;
	}

	private async void OnModalPageSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		var closeBtn = new Button
		{
			Text = "Close",
			BackgroundColor = Color.FromArgb("#F2B8B5"),
			TextColor = Colors.White
		};

		var modalPage = new ContentPage
		{
			BackgroundColor = Color.FromArgb("#80000000"),
			Content = new Grid
			{
				Children =
				{
					new Border
					{
						StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle
						{
							CornerRadius = new CornerRadius(20, 20, 0, 0)
						},
						Background = new SolidColorBrush(Color.FromArgb("#1C1B1F")),
						Stroke = new SolidColorBrush(Colors.Gray),
						StrokeThickness = 1,
						VerticalOptions = LayoutOptions.End,
						Padding = new Thickness(20),
						Content = new VerticalStackLayout
						{
							Spacing = 12,
							Children =
							{
								new BoxView
								{
									HeightRequest = 4, WidthRequest = 40, CornerRadius = 2,
									Color = Colors.Gray, HorizontalOptions = LayoutOptions.Center,
									Margin = new Thickness(0, 0, 0, 8)
								},
								new Label
								{
									Text = "Modal Page Bottom Sheet",
									FontSize = 20, FontAttributes = FontAttributes.Bold,
									TextColor = Colors.White
								},
								new Label
								{
									Text = "Presented via Navigation.PushModalAsync with a semi-transparent background. Tests whether SR captures modal page overlays.",
									TextColor = Color.FromArgb("#CAC4D0")
								},
								new Button { Text = "Option X" },
								new Button { Text = "Option Y" },
								closeBtn
							}
						}
					}
				}
			}
		};

		closeBtn.Clicked += async (s, _) => await Navigation.PopModalAsync(animated: true);
		_ = Navigation.PushModalAsync(modalPage, animated: true);

		await AutoClose(async () => {
			if (Navigation.ModalStack.Contains(modalPage))
				await Navigation.PopModalAsync(animated: true);
		});
	}

	private async void OnToolkitPopupSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		var popup = new Popup();

		var closeBtn = new Button
		{
			Text = "Close",
			BackgroundColor = Color.FromArgb("#F2B8B5"),
			TextColor = Colors.White
		};
		closeBtn.Clicked += (s, _) => popup.Close();

		popup.Content = new VerticalStackLayout
		{
			Padding = new Thickness(20),
			Spacing = 12,
			Children =
			{
				new BoxView
				{
					HeightRequest = 4, WidthRequest = 40, CornerRadius = 2,
					Color = Colors.Gray, HorizontalOptions = LayoutOptions.Center,
					Margin = new Thickness(0, 0, 0, 8)
				},
				new Label
				{
					Text = "Toolkit Popup Sheet",
					FontSize = 20, FontAttributes = FontAttributes.Bold
				},
				new Label
				{
					Text = "Rendered via CommunityToolkit.Maui Popup using its own modal overlay mechanism. Tests whether SR captures toolkit popups."
				},
				new Button { Text = "Option 1" },
				new Button { Text = "Option 2" },
				closeBtn
			}
		};

		_ = this.ShowPopupAsync(popup);

		await AutoClose(async () => {
			popup.Close();
			await Task.CompletedTask;
		});
	}

	private async void OnNativeBottomSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		NativeBottomSheet.IsVisible = true;
		NativeBottomSheet.InputTransparent = false;
		NativeBottomSheet.IsOpen = true;

		await AutoClose(async () => {
			NativeBottomSheet.IsOpen = false;
			await Task.CompletedTask;
		});
	}

	private void OnNativeBottomSheetClosed(object? sender, EventArgs e)
	{
		NativeBottomSheet.IsOpen = false;
		NativeBottomSheet.IsVisible = false;
		NativeBottomSheet.InputTransparent = true;
	}

	private async void OnOverlayDimTapped(object? sender, TappedEventArgs e)
	{
		await DismissXamlOverlay();
	}

	private async void OnXamlOverlayCloseClicked(object? sender, EventArgs e)
	{
		await DismissXamlOverlay();
	}

	private async Task DismissXamlOverlay()
	{
		if (!XamlOverlaySheet.IsVisible) return;
		await XamlOverlaySheet.TranslateTo(0, 400, 250, Easing.CubicIn);
		XamlOverlaySheet.IsVisible = false;
		XamlOverlaySheet.InputTransparent = true;
		OverlayDim.IsVisible = false;
		OverlayDim.InputTransparent = true;
	}

	// --- Tooltip ---

	private async void OnShowTooltipPopupClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		TooltipOverlay.InputTransparent = false;
		TooltipOverlay.IsVisible = true;
		TooltipOverlay.Opacity = 0;
		await TooltipOverlay.FadeTo(1, 200);

		if (double.TryParse(DelayEntry.Text, out double seconds) && seconds > 0)
		{
			await Task.Delay(TimeSpan.FromSeconds(seconds));
		}
		else
		{
			await Task.Delay(2000); // Default stay-time if delay is 0
		}
		
		await TooltipOverlay.FadeTo(0, 200);
		TooltipOverlay.IsVisible = false;
		TooltipOverlay.InputTransparent = true;
	}

#if IOS
	private UIWindow? _uiWindow;

	private async void OnUIWindowSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		var scene = UIApplication.SharedApplication.ConnectedScenes
			.ToArray()
			.OfType<UIWindowScene>()
			.FirstOrDefault();

		if (scene == null) return;

		_uiWindow = new UIWindow(scene);
		_uiWindow.WindowLevel = UIWindowLevel.Alert + 1;

		var closeBtn = new Button
		{
			Text = "Close UIWindow",
			BackgroundColor = Color.FromArgb("#F2B8B5"),
			TextColor = Colors.White
		};

		var sheetContent = new Border
		{
			StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle
			{
				CornerRadius = new CornerRadius(20, 20, 0, 0)
			},
			Background = new SolidColorBrush(Color.FromArgb("#1C1B1F")),
			Stroke = new SolidColorBrush(Colors.Gray),
			StrokeThickness = 1,
			VerticalOptions = LayoutOptions.End,
			Padding = new Thickness(20),
			Content = new VerticalStackLayout
			{
				Spacing = 12,
				Children =
				{
					new BoxView
					{
						HeightRequest = 4, WidthRequest = 40, CornerRadius = 2,
						Color = Colors.Gray, HorizontalOptions = LayoutOptions.Center,
						Margin = new Thickness(0, 0, 0, 8)
					},
					new Label
					{
						Text = "UIWindow Bottom Sheet",
						FontSize = 20, FontAttributes = FontAttributes.Bold,
						TextColor = Colors.White
					},
					new Label
					{
						Text = "This sheet lives in its own separate UIWindow on iOS. It sits on top of the entire app, including other modal pages and alerts.",
						TextColor = Color.FromArgb("#CAC4D0")
					},
					closeBtn
				}
			}
		};

		var grid = new Grid
		{
			BackgroundColor = Color.FromArgb("#80000000"),
			Children = { sheetContent }
		};

		// Convert MAUI View to Native
		var mauiContext = this.Handler?.MauiContext ?? throw new InvalidOperationException("MauiContext not found");
		var nativeView = grid.ToPlatform(mauiContext);
		
		var viewController = new UIViewController();
		viewController.View!.AddSubview(nativeView);
		nativeView.Frame = viewController.View.Bounds;
		nativeView.AutoresizingMask = UIViewAutoresizing.FlexibleWidth | UIViewAutoresizing.FlexibleHeight;
		viewController.View.BackgroundColor = UIColor.Clear;

		_uiWindow.RootViewController = viewController;
		_uiWindow.MakeKeyAndVisible();

		Func<Task> closeAction = async () =>
		{
			if (_uiWindow != null)
			{
				_uiWindow.Hidden = true;
				_uiWindow.Dispose();
				_uiWindow = null;
			}
			await Task.CompletedTask;
		};

		closeBtn.Clicked += async (s, _) => await closeAction();

		await AutoClose(closeAction);
	}
#else
	private async void OnUIWindowSheetClicked(object? sender, EventArgs e)
	{
		await WaitForDelay();
		await DisplayAlert("Not Supported", "UIWindow sheets are an iOS-specific native pattern.", "OK");
	}
#endif
}
