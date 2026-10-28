import Foundation

/// 하루 기준 새벽 6시 (docs/data-model.md 1-2).
enum OkcheDay {
    static let startHour = 6

    /// 기록의 `day`(`YYYY-MM-DD`): 현지 시각이 6시 전이면 달력의 전날.
    /// 시간을 빼지 않고 시(hour)로 판단해 서머타임 날에도 벽시계 기준으로 맞는다.
    static func of(_ date: Date, in timeZone: TimeZone = .autoupdatingCurrent) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let hour = calendar.component(.hour, from: date)
        let target = hour < startHour ? calendar.date(byAdding: .day, value: -1, to: date)! : date
        let c = calendar.dateComponents([.year, .month, .day], from: target)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}

extension Calendar {
    /// 날짜 글자(`YYYY-MM-DD`)를 만들 때 쓰는 달력: 서력 + 기기 시간대. 기기 달력(불기·일본 연호 등)과 상관없이 같은 해가 나온다.
    static var okcheDevice: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .autoupdatingCurrent
        return calendar
    }
}
