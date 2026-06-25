require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))
repo_url = package['repository']['url']
# `pod spec lint` rejects `git+` URLs and `.git` suffixes for `homepage`,
# but tolerates them in `source[:git]`. Normalize once here.
homepage_url = repo_url.sub(/^git\+/, '').sub(/\.git$/, '')
source_git = repo_url.sub(/^git\+/, '')

Pod::Spec.new do |s|
  s.name = 'GachlabCapacitorBackgroundGeolocation'
  s.version = package['version']
  s.summary = package['description']
  s.license = package['license']
  s.homepage = homepage_url
  s.author = package['author']
  s.source = { :git => source_git, :tag => s.version.to_s }
  s.source_files = 'ios/Sources/**/*.swift'
  s.ios.deployment_target = '14.0'
  s.dependency 'Capacitor'
  s.swift_version = '5.9'
  s.frameworks = 'CoreLocation', 'CoreMotion', 'UIKit', 'Network', 'UserNotifications'
  s.libraries = 'sqlite3'
  s.requires_arc = true
end
