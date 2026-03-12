using System.Collections.Generic;
using System.Linq;

#if IOS
using Foundation;
#endif

namespace LaunchDarkly.SessionReplay;

internal static class DictionaryTypeConverters
{
#if IOS
    internal static NSDictionary? ToNSDictionary(IDictionary<string, object?>? src)
    {
        if (src is null) return null;

        var keys = new List<NSObject>(src.Count);
        var vals = new List<NSObject>(src.Count);

        foreach (var (k, v) in src)
        {
            keys.Add(new NSString(k));
            vals.Add(ToNSObject(v));
        }

        return NSDictionary.FromObjectsAndKeys(vals.ToArray(), keys.ToArray());
    }

    internal static NSObject ToNSObject(object? value)
    {
        if (value is null) return NSNull.Null;

        return value switch
        {
            string s => new NSString(s),
            bool b => NSNumber.FromBoolean(b),
            int i => NSNumber.FromInt32(i),
            long l => NSNumber.FromInt64(l),
            double d => NSNumber.FromDouble(d),
            float f => NSNumber.FromFloat(f),
            decimal m => NSNumber.FromDouble((double)m),

            IEnumerable<string> arr    => NSArray.FromNSObjects(arr.Select(s => (NSObject)new NSString(s)).ToArray()),
            IEnumerable<bool> arr      => NSArray.FromNSObjects(arr.Select(b => (NSObject)NSNumber.FromBoolean(b)).ToArray()),
            IEnumerable<int> arr       => NSArray.FromNSObjects(arr.Select(i => (NSObject)NSNumber.FromInt32(i)).ToArray()),
            IEnumerable<long> arr      => NSArray.FromNSObjects(arr.Select(l => (NSObject)NSNumber.FromInt64(l)).ToArray()),
            IEnumerable<double> arr    => NSArray.FromNSObjects(arr.Select(d => (NSObject)NSNumber.FromDouble(d)).ToArray()),
            IEnumerable<float> arr     => NSArray.FromNSObjects(arr.Select(f => (NSObject)NSNumber.FromFloat(f)).ToArray()),
            IEnumerable<decimal> arr   => NSArray.FromNSObjects(arr.Select(m => (NSObject)NSNumber.FromDouble((double)m)).ToArray()),

            IDictionary<string, object> dict => ToNSDictionary(dict) ?? new NSDictionary(),

            NSDictionary nsDict => nsDict,
            NSArray nsArray     => nsArray,
            NSObject nsObj      => nsObj,

            _ => new NSString(value.ToString() ?? string.Empty)
        };
    }

#elif ANDROID
    internal static IDictionary<string, Java.Lang.Object>? ToJavaHashMap(IDictionary<string, object>? src)
    {
        if (src is null) return null;

        var map = new Dictionary<string, Java.Lang.Object>(src.Count);
        foreach (var (k, v) in src)
        {
            var jobj = ToJavaObject(v);
            if (jobj != null) map[k] = jobj;
        }
        return map;
    }

    internal static Java.Lang.Object? ToJavaObject(object? value)
    {
        if (value is null) return null;

        return value switch
        {
            string s  => new Java.Lang.String(s),
            bool b    => new Java.Lang.Boolean(b),
            int i     => new Java.Lang.Integer(i),
            long l    => new Java.Lang.Long(l),
            double d  => new Java.Lang.Double(d),
            float f   => new Java.Lang.Float(f),
            decimal m => new Java.Lang.Double((double)m),
            _ => new Java.Lang.String(value.ToString() ?? string.Empty)
        };
    }
#endif
}
