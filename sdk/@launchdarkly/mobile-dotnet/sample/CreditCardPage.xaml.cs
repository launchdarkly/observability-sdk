using LaunchDarkly.SessionReplay;

namespace MauiSample9;

public partial class CreditCardPage : ContentPage
{
	public CreditCardPage()
	{
		InitializeComponent();

		this.Loaded += OnPageLoaded;
	}

	private void OnPageLoaded(object? sender, EventArgs e)
	{
		this.Loaded -= OnPageLoaded;
		
		//this.Content?.LDIgnore();

	    BrandLabel.LDMask();
	}

	private enum CardBrand
	{
		Unknown,
		Visa,
		Mastercard,
		Amex,
		Discover
	}

	private void OnNameTextChanged(object? sender, TextChangedEventArgs e)
	{
		// Clear error as user types
		NameErrorLabel.IsVisible = false;
	}

	private void OnNumberTextChanged(object? sender, TextChangedEventArgs e)
	{
		NumberErrorLabel.IsVisible = false;

		var digits = ExtractDigits(e.NewTextValue);
		var brand = DetectBrand(digits);
		digits = LimitDigitsForBrand(digits, brand);

		var formatted = FormatNumberForBrand(digits, brand);
		if (NumberEntry.Text != formatted)
		{
			NumberEntry.Text = formatted;
		}

		BrandLabel.Text = BrandToDisplayName(brand);
		MaskedNumberLabel.Text = GetMaskedLast4(digits);

		// Adjust CVC length if needed when brand changes
		AdjustCvcForBrand(brand);
	}

	private void OnExpiryTextChanged(object? sender, TextChangedEventArgs e)
	{
		ExpiryErrorLabel.IsVisible = false;

		var digits = ExtractDigits(e.NewTextValue);
		if (digits.Length > 4)
			digits = digits.Substring(0, 4);

		string formatted;
		if (digits.Length <= 2)
		{
			formatted = digits;
		}
		else
		{
			var mm = digits.Substring(0, 2);
			var yy = digits.Substring(2);
			formatted = $"{mm}/{yy}";
		}

		if (ExpiryEntry.Text != formatted)
		{
			ExpiryEntry.Text = formatted;
		}
	}

	private void OnCvcTextChanged(object? sender, TextChangedEventArgs e)
	{
		CvcErrorLabel.IsVisible = false;

		var digits = ExtractDigits(e.NewTextValue);
		var brand = DetectBrand(ExtractDigits(NumberEntry.Text ?? string.Empty));
		var maxLen = brand == CardBrand.Amex ? 4 : 3;
		if (digits.Length > maxLen)
			digits = digits.Substring(0, maxLen);

		if (CvcEntry.Text != digits)
		{
			CvcEntry.Text = digits;
		}
	}

	private async void OnSaveClicked(object? sender, EventArgs e)
	{
		var isValid = ValidateAll(out var brand, out var digitsOnlyNumber);
		if (!isValid)
			return;

		var masked = GetMaskedLast4(digitsOnlyNumber);
		await DisplayAlert("Saved", $"{BrandToDisplayName(brand)} {masked} saved.", "OK");
	}

	private bool ValidateAll(out CardBrand brand, out string digitsOnlyNumber)
	{
		var ok = true;

		// Name
		if (string.IsNullOrWhiteSpace(NameEntry.Text) || NameEntry.Text!.Trim().Length < 2)
		{
			NameErrorLabel.Text = "Enter the cardholder name.";
			NameErrorLabel.IsVisible = true;
			ok = false;
		}
		else
		{
			NameErrorLabel.IsVisible = false;
		}

		// Number
		digitsOnlyNumber = ExtractDigits(NumberEntry.Text ?? string.Empty);
		brand = DetectBrand(digitsOnlyNumber);
		var expectedLen = ExpectedPanLengthForBrand(brand);
		if (digitsOnlyNumber.Length != expectedLen || !IsValidLuhn(digitsOnlyNumber))
		{
			NumberErrorLabel.Text = "Enter a valid card number.";
			NumberErrorLabel.IsVisible = true;
			ok = false;
		}
		else
		{
			NumberErrorLabel.IsVisible = false;
		}

		// Expiry
		if (!TryParseExpiry(ExpiryEntry.Text ?? string.Empty, out var expiryDate) || IsExpired(expiryDate))
		{
			ExpiryErrorLabel.Text = "Enter a valid future date.";
			ExpiryErrorLabel.IsVisible = true;
			ok = false;
		}
		else
		{
			ExpiryErrorLabel.IsVisible = false;
		}

		// CVC
		var cvcDigits = ExtractDigits(CvcEntry.Text ?? string.Empty);
		var cvcLen = brand == CardBrand.Amex ? 4 : 3;
		if (cvcDigits.Length != cvcLen)
		{
			CvcErrorLabel.Text = $"CVC must be {cvcLen} digits.";
			CvcErrorLabel.IsVisible = true;
			ok = false;
		}
		else
		{
			CvcErrorLabel.IsVisible = false;
		}

		return ok;
	}

	private static string ExtractDigits(string? value)
	{
		if (string.IsNullOrEmpty(value))
			return string.Empty;
		var chars = new List<char>(value.Length);
		for (int i = 0; i < value.Length; i++)
		{
			var c = value[i];
			if (c >= '0' && c <= '9')
				chars.Add(c);
		}
		return new string(chars.ToArray());
	}

	private static CardBrand DetectBrand(string digits)
	{
		if (digits.Length == 0)
			return CardBrand.Unknown;

		// Visa: 4
		if (digits.StartsWith("4", StringComparison.Ordinal))
			return CardBrand.Visa;

		// Amex: 34, 37
		if (digits.StartsWith("34", StringComparison.Ordinal) || digits.StartsWith("37", StringComparison.Ordinal))
			return CardBrand.Amex;

		// Mastercard: 51-55 or 2221-2720
		if (digits.Length >= 2)
		{
			if (int.TryParse(digits.Substring(0, Math.Min(2, digits.Length)), out var two))
			{
				if (two >= 51 && two <= 55)
					return CardBrand.Mastercard;
			}
		}
		if (digits.Length >= 4)
		{
			if (int.TryParse(digits.Substring(0, 4), out var four))
			{
				if (four >= 2221 && four <= 2720)
					return CardBrand.Mastercard;
			}
		}

		// Discover: 6011, 65, 644-649
		if (digits.StartsWith("6011", StringComparison.Ordinal) ||
		    digits.StartsWith("65", StringComparison.Ordinal))
			return CardBrand.Discover;
		if (digits.Length >= 3)
		{
			if (int.TryParse(digits.Substring(0, 3), out var three))
			{
				if (three >= 644 && three <= 649)
					return CardBrand.Discover;
			}
		}

		return CardBrand.Unknown;
	}

	private static int ExpectedPanLengthForBrand(CardBrand brand)
	{
		switch (brand)
		{
			case CardBrand.Amex:
				return 15;
			case CardBrand.Visa:
			case CardBrand.Mastercard:
			case CardBrand.Discover:
			case CardBrand.Unknown:
			default:
				return 16;
		}
	}

	private static string LimitDigitsForBrand(string digits, CardBrand brand)
	{
		var max = ExpectedPanLengthForBrand(brand);
		return digits.Length > max ? digits.Substring(0, max) : digits;
	}

	private static string FormatNumberForBrand(string digits, CardBrand brand)
	{
		if (digits.Length == 0)
			return string.Empty;

		if (brand == CardBrand.Amex)
		{
			// 4-6-5
			var parts = new List<string>(3);
			if (digits.Length <= 4) return digits;
			parts.Add(digits.Substring(0, 4));
			if (digits.Length <= 10)
			{
				parts.Add(digits.Substring(4));
			}
			else
			{
				parts.Add(digits.Substring(4, 6));
				parts.Add(digits.Substring(10));
			}
			return string.Join(" ", parts);
		}
		else
		{
			// Groups of 4
			var parts = new List<string>();
			for (int i = 0; i < digits.Length; i += 4)
			{
				var len = Math.Min(4, digits.Length - i);
				parts.Add(digits.Substring(i, len));
			}
			return string.Join(" ", parts);
		}
	}

	private static string BrandToDisplayName(CardBrand brand)
	{
		switch (brand)
		{
			case CardBrand.Visa: return "Visa";
			case CardBrand.Mastercard: return "Mastercard";
			case CardBrand.Amex: return "American Express";
			case CardBrand.Discover: return "Discover";
			default: return "Card";
		}
	}

	private static string GetMaskedLast4(string digits)
	{
		if (string.IsNullOrEmpty(digits))
			return string.Empty;
		var last4 = digits.Length <= 4 ? digits : digits.Substring(digits.Length - 4);
		return $"•••• {last4}";
	}

	private void AdjustCvcForBrand(CardBrand brand)
	{
		var digits = ExtractDigits(CvcEntry.Text ?? string.Empty);
		var maxLen = brand == CardBrand.Amex ? 4 : 3;
		if (digits.Length > maxLen)
			digits = digits.Substring(0, maxLen);
		if (CvcEntry.Text != digits)
			CvcEntry.Text = digits;
	}

	private static bool IsValidLuhn(string digits)
	{
		if (digits.Length < 12)
			return false;

		int sum = 0;
		bool doubleIt = false;
		for (int i = digits.Length - 1; i >= 0; i--)
		{
			int d = digits[i] - '0';
			if (d < 0 || d > 9) return false;
			if (doubleIt)
			{
				d *= 2;
				if (d > 9) d -= 9;
			}
			sum += d;
			doubleIt = !doubleIt;
		}
		return sum % 10 == 0;
	}

	private static bool TryParseExpiry(string value, out DateTime lastMomentOfMonthUtc)
	{
		lastMomentOfMonthUtc = default;
		var parts = value.Split('/');
		if (parts.Length != 2)
			return false;
		if (parts[0].Length != 2 || parts[1].Length != 2)
			return false;
		if (!int.TryParse(parts[0], out var mm) || !int.TryParse(parts[1], out var yy))
			return false;
		if (mm < 1 || mm > 12)
			return false;

		// Map YY to 2000-2099
		int year = 2000 + yy;
		try
		{
			var firstOfMonth = new DateTime(year, mm, 1, 0, 0, 0, DateTimeKind.Utc);
			var firstOfNext = firstOfMonth.AddMonths(1);
			lastMomentOfMonthUtc = firstOfNext.AddMilliseconds(-1);
			return true;
		}
		catch
		{
			return false;
		}
	}

	private static bool IsExpired(DateTime lastMomentOfMonthUtc)
	{
		// Consider current time in UTC
		return DateTime.UtcNow > lastMomentOfMonthUtc;
	}
}


