#if IOS
using System.Collections.Generic;
using System.Linq;
using Foundation;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Json;

namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Converts between C# LdValue and Foundation types (NSObject hierarchy)
    /// so values can cross the MAUI / Obj-C boundary without JSON string parsing.
    ///
    /// Mapping:
    ///   LdValueType.Null   <-> NSNull
    ///   LdValueType.Bool   <-> NSNumber (boolean)
    ///   LdValueType.Number <-> NSNumber (double)
    ///   LdValueType.String <-> NSString
    ///   LdValueType.Array  <-> NSArray
    ///   LdValueType.Object <-> NSDictionary
    /// </summary>
    internal static class LdValueBridge
    {
        internal static NSObject ToNative(LdValue value)
        {
            switch (value.Type)
            {
                case LdValueType.Bool:
                    return NSNumber.FromBoolean(value.AsBool);
                case LdValueType.Number:
                    return NSNumber.FromDouble(value.AsDouble);
                case LdValueType.String:
                    return new NSString(value.AsString);
                case LdValueType.Array:
                    var items = value.AsList(LdValue.Convert.Json)
                        .Select(ToNative).ToArray();
                    return NSArray.FromNSObjects(items);
                case LdValueType.Object:
                    var dict = value.AsDictionary(LdValue.Convert.Json);
                    var keys = new List<NSObject>(dict.Count);
                    var vals = new List<NSObject>(dict.Count);
                    foreach (var kvp in dict)
                    {
                        keys.Add(new NSString(kvp.Key));
                        vals.Add(ToNative(kvp.Value));
                    }
                    return NSDictionary.FromObjectsAndKeys(vals.ToArray(), keys.ToArray());
                default:
                    return NSNull.Null;
            }
        }

        internal static LdValue FromNative(NSObject obj)
        {
            if (obj == null || obj is NSNull) return LdValue.Null;

            if (obj is NSNumber num)
            {
                var objCType = num.ObjCType;
                if (objCType == "c" || objCType == "B")
                    return LdValue.Of(num.BoolValue);
                return LdValue.Of(num.DoubleValue);
            }

            if (obj is NSString str)
                return LdValue.Of(str.ToString());

            if (obj is NSArray arr)
            {
                var builder = LdValue.BuildArray();
                for (nuint i = 0; i < arr.Count; i++)
                    builder.Add(FromNative(arr.GetItem<NSObject>(i)));
                return builder.Build();
            }

            if (obj is NSDictionary dict)
            {
                var builder = LdValue.BuildObject();
                foreach (var key in dict.Keys)
                    builder.Add(key.ToString(), FromNative(dict[key]));
                return builder.Build();
            }

            return LdValue.Null;
        }

        /// <summary>
        /// Converts an EvaluationReason to an NSDictionary by serializing through LdValue.
        /// </summary>
        internal static NSDictionary ReasonToNative(EvaluationReason? reason)
        {
            if (!reason.HasValue) return new NSDictionary();
            try
            {
                var json = LdJsonSerialization.SerializeObject(reason.Value);
                var ldValue = LdValue.Parse(json);
                var native = ToNative(ldValue);
                return native as NSDictionary ?? new NSDictionary();
            }
            catch
            {
                return new NSDictionary();
            }
        }
    }
}
#endif
