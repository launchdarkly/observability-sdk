using System.Collections.Generic;
using System.Linq;

#if IOS
using Foundation;
#endif

namespace LaunchDarkly.Observability;

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

            IDictionary<string, object?> dict => ToNSDictionary(dict) ?? new NSDictionary(),

            NSDictionary nsDict => nsDict,
            NSArray nsArray     => nsArray,
            NSObject nsObj      => nsObj,

            _ => new NSString(value.ToString() ?? string.Empty)
        };
    }

#elif ANDROID
    // Returns a managed Dictionary (not Java.Util.HashMap) intentionally.
    // The auto-generated Android binding marshals any IDictionary<K,V> into a
    // java.util.HashMap via JavaDictionary<K,V>.ToLocalJniHandle() at JNI call
    // time — changing this to Java.Util.HashMap causes double-marshaling bugs.
    internal static IDictionary<string, Java.Lang.Object>? ToJavaDictionary(IDictionary<string, object?>? src)
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
            bool b    => Java.Lang.Boolean.ValueOf(b),
            int i     => Java.Lang.Integer.ValueOf(i),
            long l    => Java.Lang.Long.ValueOf(l),
            double d  => Java.Lang.Double.ValueOf(d),
            float f   => Java.Lang.Float.ValueOf(f),
            decimal m => Java.Lang.Double.ValueOf((double)m),

            IDictionary<string, object?> dict => ToJavaHashMap(dict),

            IEnumerable<string> arr  => ToJavaList(arr.Select(s => (Java.Lang.Object)new Java.Lang.String(s))),
            IEnumerable<bool> arr    => ToJavaList(arr.Select(b => (Java.Lang.Object)Java.Lang.Boolean.ValueOf(b))),
            IEnumerable<int> arr     => ToJavaList(arr.Select(i => (Java.Lang.Object)Java.Lang.Integer.ValueOf(i))),
            IEnumerable<long> arr    => ToJavaList(arr.Select(l => (Java.Lang.Object)Java.Lang.Long.ValueOf(l))),
            IEnumerable<double> arr  => ToJavaList(arr.Select(d => (Java.Lang.Object)Java.Lang.Double.ValueOf(d))),
            IEnumerable<float> arr   => ToJavaList(arr.Select(f => (Java.Lang.Object)Java.Lang.Float.ValueOf(f))),

            _ => new Java.Lang.String(value.ToString() ?? string.Empty)
        };
    }

    private static Java.Util.HashMap ToJavaHashMap<TValue>(IDictionary<string, TValue> dict)
    {
        var map = new Java.Util.HashMap();
        foreach (var (k, v) in dict)
        {
            var jVal = ToJavaObject(v);
            if (jVal != null) map.Put(new Java.Lang.String(k), jVal);
        }
        return map;
    }

    private static Java.Util.ArrayList ToJavaList(IEnumerable<Java.Lang.Object> items)
    {
        var list = new Java.Util.ArrayList();
        foreach (var item in items)
            list.Add(item);
        return list;
    }
#endif
}
