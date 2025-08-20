import Testing
import Shared

struct StringifyableObject: Encodable {
    let name: String
}

@Test func stringify() {
    let value = "hello"
    let stringifyed = JSON.stringify(value)
    #expect(stringifyed == "\"hello\"")
    
    let boolValue = true
    let boolStringifyed = JSON.stringify(boolValue)
    #expect(boolStringifyed == "true")
    
    let doubleValue: Double = 1.234
    let doubleStringifyed = JSON.stringify(doubleValue)
    #expect(doubleStringifyed == "1.234")
    
    let obj = StringifyableObject(name: "John")
    let string = JSON.stringify(obj)
    #expect(string == "{\"name\":\"John\"}")
}
