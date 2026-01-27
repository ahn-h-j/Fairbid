output "ec2_public_ip" {
  description = "EC2 Elastic IP (고정)"
  value       = aws_eip.fairbid.public_ip
}

output "ec2_instance_id" {
  description = "EC2 인스턴스 ID"
  value       = aws_instance.fairbid.id
}

output "nameservers" {
  description = "Route 53 네임서버 목록 (가비아에 이 값들을 입력)"
  value       = aws_route53_zone.fairbid.name_servers
}

output "domain_name" {
  description = "설정된 도메인"
  value       = var.domain_name
}
